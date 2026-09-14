import { Pipe, PipeTransform } from '@angular/core';
import { dataCurta } from './datas';

/** Mostra uma data ISO no formato brasileiro, sem deslocar por fuso. */
@Pipe({ name: 'dataBr' })
export class DataBrPipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return dataCurta(iso);
  }
}
