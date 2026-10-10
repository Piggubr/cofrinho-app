import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Icones de traço do app.
 *
 * <p>Os desenhos vem do Lucide (lucide.dev, licenca ISC), copiados aqui como dados para
 * nao trazer uma biblioteca inteira por causa de trinta icones. Cada icone e uma lista
 * de formas SVG desenhadas numa grade de 24x24, com traço de 2px e pontas arredondadas;
 * a cor vem de currentColor, entao segue a cor do texto em volta.</p>
 */
type Forma =
  | { readonly t: 'path'; readonly d: string }
  | { readonly t: 'circle'; readonly cx: number; readonly cy: number; readonly r: number }
  | {
      readonly t: 'rect';
      readonly x: number;
      readonly y: number;
      readonly w: number;
      readonly h: number;
      readonly rx: number;
    }
  | { readonly t: 'poly'; readonly points: string; readonly fechado?: boolean };

const p = (d: string): Forma => ({ t: 'path', d });
const c = (cx: number, cy: number, r: number): Forma => ({ t: 'circle', cx, cy, r });
const r = (x: number, y: number, w: number, h: number, rx: number): Forma => ({
  t: 'rect',
  x,
  y,
  w,
  h,
  rx,
});

const ICONES = {
  casa: [
    p('M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8'),
    p(
      'M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
    ),
  ],
  carteira: [
    p(
      'M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1',
    ),
    p('M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4'),
  ],
  entrada: [p('M22 7 13.5 15.5 8.5 10.5 2 17'), p('M16 7h6v6')],
  grafico: [p('M3 3v16a2 2 0 0 0 2 2h16'), p('M18 17V9'), p('M13 17V5'), p('M8 17v-3')],
  cartao: [r(2, 5, 20, 14, 2), p('M2 10h20')],
  banco: [
    p('M3 22h18'),
    p('M6 18v-7'),
    p('M10 18v-7'),
    p('M14 18v-7'),
    p('M18 18v-7'),
    { t: 'poly', points: '12 2 20 7 4 7', fechado: true },
  ],
  recibo: [
    p('M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z'),
    p('M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8'),
    p('M12 17.5v-11'),
  ],
  pizza: [p('M21.21 15.89A10 10 0 1 1 8 2.83'), p('M22 12A10 10 0 0 0 12 2v10z')],
  calendario: [p('M8 2v4'), p('M16 2v4'), r(3, 4, 18, 18, 2), p('M3 10h18')],
  carrinho: [
    c(8, 21, 1),
    c(19, 21, 1),
    p('M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12'),
  ],
  local: [
    p(
      'M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0',
    ),
    c(12, 10, 3),
  ],
  filme: [
    r(3, 3, 18, 18, 2),
    p('M7 3v18'),
    p('M3 7.5h4'),
    p('M3 12h18'),
    p('M3 16.5h4'),
    p('M17 3v18'),
    p('M17 7.5h4'),
    p('M17 16.5h4'),
  ],
  imagem: [r(3, 3, 18, 18, 2), c(9, 9, 2), p('m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21')],
  camera: [
    p('M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z'),
    c(12, 13, 3),
  ],
  trofeu: [
    p('M6 9H4.5a2.5 2.5 0 0 1 0-5H6'),
    p('M18 9h1.5a2.5 2.5 0 0 0 0-5H18'),
    p('M4 22h16'),
    p('M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22'),
    p('M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22'),
    p('M18 2H6v7a6 6 0 0 0 12 0V2Z'),
  ],
  alvo: [c(12, 12, 10), c(12, 12, 6), c(12, 12, 2)],
  pessoas: [
    p('M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2'),
    c(9, 7, 4),
    p('M22 21v-2a4 4 0 0 0-3-3.87'),
    p('M16 3.13a4 4 0 0 1 0 7.75'),
  ],
  pessoa: [p('M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2'), c(12, 7, 4)],
  estrela: [
    {
      t: 'poly',
      points:
        '12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2',
      fechado: true,
    },
  ],
  sair: [p('M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'), p('m16 17 5-5-5-5'), p('M21 12H9')],
  busca: [c(11, 11, 8), p('m21 21-4.3-4.3')],
  mais: [p('M5 12h14'), p('M12 5v14')],
  fechar: [p('M18 6 6 18'), p('m6 6 12 12')],
  menu: [p('M4 6h16'), p('M4 12h16'), p('M4 18h16')],
  grade: [r(3, 3, 7, 7, 1), r(14, 3, 7, 7, 1), r(14, 14, 7, 7, 1), r(3, 14, 7, 7, 1)],
  esquerda: [p('m15 18-6-6 6-6')],
  direita: [p('m9 18 6-6-6-6')],
  lixeira: [
    p('M3 6h18'),
    p('M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6'),
    p('M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2'),
  ],
  lapis: [
    p(
      'M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z',
    ),
    p('m15 5 4 4'),
  ],
  check: [p('M20 6 9 17l-5-5')],
  alerta: [
    p('m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3'),
    p('M12 9v4'),
    p('M12 17h.01'),
  ],
  cofrinho: [
    p(
      'M19 5c-1.5 0-2.8 1.4-3 2-3.5-1.5-11-.3-11 5 0 1.8 0 3 2 4.5V20h4v-2h3v2h4v-4c1-.5 1.7-1 2-2h2v-4h-2c0-1-.5-1.5-1-2V5z',
    ),
    p('M2 9v1c0 1.1.9 2 2 2h1'),
    p('M16 11h.01'),
  ],
} satisfies Record<string, readonly Forma[]>;

export type NomeDoIcone = keyof typeof ICONES;

@Component({
  selector: 'app-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'icone', 'aria-hidden': 'true' },
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      [attr.width]="tamanho()"
      [attr.height]="tamanho()"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="traco()"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      @for (forma of formas(); track $index) {
        @switch (forma.t) {
          @case ('path') {
            <svg:path [attr.d]="forma.d" />
          }
          @case ('circle') {
            <svg:circle [attr.cx]="forma.cx" [attr.cy]="forma.cy" [attr.r]="forma.r" />
          }
          @case ('rect') {
            <svg:rect
              [attr.x]="forma.x"
              [attr.y]="forma.y"
              [attr.width]="forma.w"
              [attr.height]="forma.h"
              [attr.rx]="forma.rx"
            />
          }
          @case ('poly') {
            <svg:polygon [attr.points]="forma.points" />
          }
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-shrink: 0;
      line-height: 0;
    }
  `,
})
export class Icone {
  readonly nome = input.required<NomeDoIcone>();
  readonly tamanho = input(20);
  readonly traco = input(1.8);

  protected readonly formas = computed<readonly Forma[]>(() => ICONES[this.nome()]);
}
