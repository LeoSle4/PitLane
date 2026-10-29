import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteObject, getBytes, ref, uploadBytes } from 'firebase/storage';
import { after, before, beforeEach, describe, it } from 'node:test';
import { crearEntorno, sembrar, sesiones } from './entorno.mjs';

let entorno;
let s;
const foto = (contexto, ruta) => ref(contexto.storage(), ruta);
const bytes = (tamano = 10) => new Uint8Array(tamano);
const JPG = { contentType: 'image/jpeg' };

before(async () => { entorno = await crearEntorno('demo-pitlane'); });
after(async () => { await entorno.cleanup(); });
beforeEach(async () => {
  await entorno.clearFirestore();
  await entorno.clearStorage();
  await sembrar(entorno);
  s = sesiones(entorno);
  await entorno.withSecurityRulesDisabled(async (contexto) => {
    await uploadBytes(ref(contexto.storage(), 'fotos/c1/existente.jpg'), bytes(), JPG);
  });
});

describe('fotos', () => {
  it('el cliente sube y lee en su carpeta, y no en la de otro cliente', async () => {
    await assertSucceeds(uploadBytes(foto(s.c1, 'fotos/c1/nueva.jpg'), bytes(), JPG));
    await assertSucceeds(getBytes(foto(s.c1, 'fotos/c1/existente.jpg')));
    await assertFails(uploadBytes(foto(s.c1, 'fotos/c2/nueva.jpg'), bytes(), JPG));
    await assertFails(getBytes(foto(s.c2, 'fotos/c1/existente.jpg')));
  });

  it('solo admite JPG y PNG de hasta 5 MB', async () => {
    await assertSucceeds(uploadBytes(foto(s.c1, 'fotos/c1/a.png'), bytes(), { contentType: 'image/png' }));
    await assertFails(uploadBytes(foto(s.c1, 'fotos/c1/a.gif'), bytes(), { contentType: 'image/gif' }));
    await assertFails(uploadBytes(foto(s.c1, 'fotos/c1/a.pdf'), bytes(), { contentType: 'application/pdf' }));
    await assertSucceeds(uploadBytes(foto(s.c1, 'fotos/c1/justa.jpg'), bytes(5 * 1024 * 1024), JPG));
    await assertFails(uploadBytes(foto(s.c1, 'fotos/c1/grande.jpg'), bytes(5 * 1024 * 1024 + 1), JPG));
  });

  it('se puede volver a subir una foto con el mismo nombre tras un reintento', async () => {
    await assertSucceeds(uploadBytes(foto(s.c1, 'fotos/c1/existente.jpg'), bytes(20), JPG));
  });

  it('el técnico con una orden del cliente lee y sube fotos en su carpeta, y no en la de otros', async () => {
    await assertSucceeds(getBytes(foto(s.t1, 'fotos/c1/existente.jpg')));
    await assertSucceeds(uploadBytes(foto(s.t1, 'fotos/c1/avance.jpg'), bytes(), JPG));
    await assertFails(uploadBytes(foto(s.t1, 'fotos/c2/avance.jpg'), bytes(), JPG));
    await assertFails(getBytes(foto(s.t2, 'fotos/c1/existente.jpg')));
  });

  it('el Jefe lee, sube y elimina fotos de cualquier cliente', async () => {
    await assertSucceeds(getBytes(foto(s.jefe, 'fotos/c1/existente.jpg')));
    await assertSucceeds(uploadBytes(foto(s.jefe, 'fotos/c2/orden.jpg'), bytes(), JPG));
    await assertSucceeds(deleteObject(foto(s.jefe, 'fotos/c1/existente.jpg')));
  });

  it('eliminan fotos el dueño de la carpeta y quienes tienen acceso, no un tercero', async () => {
    await assertFails(deleteObject(foto(s.c2, 'fotos/c1/existente.jpg')));
    await assertFails(deleteObject(foto(s.t2, 'fotos/c1/existente.jpg')));
    await assertSucceeds(deleteObject(foto(s.c1, 'fotos/c1/existente.jpg')));
  });

  it('sin sesión válida no se lee ni se sube nada', async () => {
    await assertFails(getBytes(foto(s.anonimo, 'fotos/c1/existente.jpg')));
    await assertFails(uploadBytes(foto(s.anonimo, 'fotos/c1/nueva.jpg'), bytes(), JPG));
    await assertFails(getBytes(foto(s.sinReclamos, 'fotos/c1/existente.jpg')));
  });

  it('fuera de la carpeta fotos no se escribe nada', async () => {
    await assertFails(uploadBytes(foto(s.jefe, 'otros/archivo.jpg'), bytes(), JPG));
  });
});
