import { Component, DestroyRef, afterNextRender, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { levarAoErro } from './core/ui/levar-ao-erro';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  constructor() {
    const destruir = inject(DestroyRef);
    // Erro de formulario fora da vista: rola ate ele, em todas as telas (ver levarAoErro).
    afterNextRender(() => destruir.onDestroy(levarAoErro(document.body)));
  }
}
