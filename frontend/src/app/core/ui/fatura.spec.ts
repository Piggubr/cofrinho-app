import { faturaDaCompra, fechamentoPadrao } from './fatura';

describe('fatura do cartão', () => {
  it('fecha 7 dias antes do vencimento, voltando ao mês anterior', () => {
    expect(fechamentoPadrao(15)).toBe(8);
    expect(fechamentoPadrao(5)).toBe(29);
  });

  it('na véspera do fechamento cai na fatura do mês; no dia do fechamento, na seguinte', () => {
    expect(faturaDaCompra(8, 15, '2026-10-07')).toEqual({ mes: '2026-10', vencimento: '2026-10-15' });
    expect(faturaDaCompra(8, 15, '2026-10-08')).toEqual({ mes: '2026-11', vencimento: '2026-11-15' });
  });

  it('vencimento antes do fechamento fica no mês seguinte; dia 31 vira o fim do mês curto', () => {
    expect(faturaDaCompra(29, 5, '2026-10-30')).toEqual({ mes: '2026-11', vencimento: '2026-12-05' });
    expect(faturaDaCompra(31, 10, '2026-02-28')).toEqual({ mes: '2026-03', vencimento: '2026-04-10' });
  });
});
