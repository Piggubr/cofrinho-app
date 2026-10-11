import { INTERCEPTORES_DA_DEMO, MODO_DEMO, imagemDaDemo } from './modo-demo';

/**
 * O build normal (e os testes) usa este arquivo: a demonstracao fica desligada.
 * So o build "demo" troca por modo-demo.demo.ts (fileReplacements no angular.json),
 * e o CI confere que o bundle de producao nao tem nada da familia de exemplo.
 */
describe('modo demo fora do build demo', () => {
  it('fica desligado: nenhum interceptor e nenhuma imagem em memoria', () => {
    expect(MODO_DEMO).toBe(false);
    expect(INTERCEPTORES_DA_DEMO).toEqual([]);
    expect(imagemDaDemo('qualquer')).toBeNull();
  });
});
