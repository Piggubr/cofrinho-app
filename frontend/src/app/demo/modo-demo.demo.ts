import { HttpInterceptorFn } from '@angular/common/http';
import { apiDaDemo } from './api-da-demo';

/** Build "demo": o app conversa com a API falsa em memoria, sem backend. */
export const MODO_DEMO = true;

export const INTERCEPTORES_DA_DEMO: HttpInterceptorFn[] = [apiDaDemo];

export { imagemDaDemo } from './api-da-demo';
