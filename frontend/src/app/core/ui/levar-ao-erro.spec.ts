import { levarAoErro } from './levar-ao-erro';

describe('levarAoErro', () => {
  const foraDaTela = { top: -500, bottom: -460 } as DOMRect;

  it('rola até o erro que aparece fora da vista e ignora o resto', async () => {
    const raiz = document.createElement('div');
    document.body.append(raiz);
    const desligar = levarAoErro(raiz);
    const rolar = vi.fn();
    const aviso = document.createElement('p');
    aviso.className = 'aviso sucesso';
    raiz.append(aviso);
    const erro = document.createElement('p');
    erro.className = 'aviso erro';
    erro.getBoundingClientRect = () => foraDaTela;
    erro.scrollIntoView = rolar;

    raiz.append(erro);
    await Promise.resolve();

    expect(rolar).toHaveBeenCalledTimes(1);
    expect(erro.classList).toContain('chamando');
    desligar();
    raiz.remove();
  });

  it('o mesmo erro de novo, depois de tocar no botão de salvar, leva de novo', async () => {
    const raiz = document.createElement('div');
    raiz.innerHTML = '<p class="aviso erro">Falta o dia</p><div class="acoes"><button>Salvar</button></div>';
    document.body.append(raiz);
    const erro = raiz.querySelector<HTMLElement>('.aviso.erro')!;
    erro.getBoundingClientRect = () => foraDaTela;
    erro.scrollIntoView = vi.fn();
    const desligar = levarAoErro(raiz);

    raiz.querySelector('button')!.click();
    await new Promise((fim) => setTimeout(fim));

    expect(erro.scrollIntoView).toHaveBeenCalled();
    desligar();
    raiz.remove();
  });
});
