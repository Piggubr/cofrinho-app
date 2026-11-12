import { HttpErrorResponse } from '@angular/common/http';
import { mensagemDeErro } from './mensagem-de-erro';

/**
 * Leitura da mensagem de erro.
 *
 * <p>Todos os servicos respondem no mesmo formato, com o campo erro ja escrito
 * para o usuario. O trabalho aqui e nunca mostrar um objeto cru na tela.</p>
 */
describe('mensagemDeErro', () => {
  it('usa a mensagem que o backend escreveu', () => {
    const erro = new HttpErrorResponse({
      status: 422,
      error: { erro: 'O saldo nao pode ficar negativo.', codigo: 'BusinessException' },
    });

    expect(mensagemDeErro(erro)).toBe('O saldo nao pode ficar negativo.');
  });

  it('avisa sobre conexao quando nao houve resposta', () => {
    const erro = new HttpErrorResponse({ status: 0 });

    expect(mensagemDeErro(erro)).toContain('Sem conexão');
  });

  it('servico fora do ar (503) ou lento (504) sem mensagem da API mostra o texto do contrato', () => {
    const texto = 'Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.';
    expect(mensagemDeErro(new HttpErrorResponse({ status: 503, error: { codigo: 'SERVICO_INDISPONIVEL' } }))).toBe(texto);
    expect(mensagemDeErro(new HttpErrorResponse({ status: 504, error: '<html>Gateway</html>' }))).toBe(texto);
    // A mensagem propria do servico (o gateway manda a do contrato) continua valendo.
    const doServico = new HttpErrorResponse({ status: 503, error: { erro: 'Catálogo fora do ar.' } });
    expect(mensagemDeErro(doServico)).toBe('Catálogo fora do ar.');
  });

  it('aceita corpo em texto puro', () => {
    const erro = new HttpErrorResponse({ status: 500, error: 'Falha bruta' });

    expect(mensagemDeErro(erro)).toBe('Falha bruta');
  });

  it('cai no padrao quando o corpo nao traz mensagem', () => {
    const erro = new HttpErrorResponse({ status: 500, error: { algo: 'inesperado' } });

    expect(mensagemDeErro(erro, 'Tente de novo.')).toBe('Tente de novo.');
  });

  it('cai no padrao quando nem e um erro HTTP', () => {
    expect(mensagemDeErro(new Error('interno'), 'Tente de novo.')).toBe('Tente de novo.');
  });
});
