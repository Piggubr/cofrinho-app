import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AvisoDeCookies } from './core/privacidade/aviso-de-cookies';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AvisoDeCookies],
  template: '<router-outlet /><app-aviso-de-cookies />',
})
export class App {}
