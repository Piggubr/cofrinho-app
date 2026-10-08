import { HttpErrorResponse } from '@angular/common/http';
import { codigoDoErro, pedeConsentimento } from './aviso';

describe('aviso de privacidade', () => {
  it('reconhece quando o backend pede autorizacao', () => {
    const pedido = new HttpErrorResponse({
      status: 422,
      error: { erro: 'Autorize', codigo: 'CONSENTIMENTO_NECESSARIO', campos: [], momento: '' },
    });
    expect(pedeConsentimento(pedido)).toBe(true);
    expect(pedeConsentimento(new HttpErrorResponse({ status: 500 }))).toBe(false);
    expect(codigoDoErro(new Error('x'))).toBeNull();
  });
});
