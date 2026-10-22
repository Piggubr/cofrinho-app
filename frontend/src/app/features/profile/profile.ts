import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FinanceService } from '../../core/api/finance.service';
import { FormsModule } from '@angular/forms';
import { Observable, firstValueFrom } from 'rxjs';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UsersService } from '../../core/api/users.service';
import { AvisosNoCelular } from '../../core/nativo/avisos-no-celular';
import { PessoasDaFamilia } from '../../core/api/pessoas-da-familia';
import { MoedaDisponivel, PigguRole, Usuario } from '../../core/api/models';
import { baixar, prepararFotoDePerfil } from '../../core/ui/arquivo';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { Icone } from '../../core/ui/icone';
import { BLOCOS_DO_PAINEL, IdDoBloco, PainelOrganizado } from '../../core/ui/painel';
import {
  IdDoTema,
  MODOS,
  ModoDeAparencia,
  TEMAS,
  Temas,
  esquecerQueVoltouAoPadrao,
  voltouAoPadrao,
} from '../../core/ui/tema';
import { ICONES_DO_APP, IconesDoApp, IdDoIcone } from '../../core/nativo/icone-do-app';
import { Tutorial } from '../../core/ui/tutorial';
import { AutorizacoesDeIa } from './autorizacoes-de-ia';
import { problemaDaSenha } from '../../core/auth/regras-da-senha';
import { Carregando } from '../../core/ui/carregando';

/**
 * Perfil e, para o administrador, as contas da instalacao.
 *
 * <p>O cadastro e aberto: quem entra em cada familia e assunto do titular, na tela
 * Familia.</p>
 */
@Component({
  selector: 'app-profile',
  imports: [FormsModule, RouterLink, Icone, AutorizacoesDeIa, Carregando],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly users = inject(UsersService);
  protected readonly auth = inject(AuthService);
  protected readonly tutorial = inject(Tutorial);
  private readonly temasDoApp = inject(Temas);
  protected readonly temas = TEMAS;
  protected readonly temaAtual = this.temasDoApp.atual;
  protected readonly modos = MODOS;
  protected readonly modoAtual = this.temasDoApp.modo;
  protected readonly iconesDoApp = inject(IconesDoApp);
  protected readonly icones = ICONES_DO_APP;
  protected readonly iconeAtual = this.iconesDoApp.atual;
  protected readonly voltouAoPadrao = signal(voltouAoPadrao());
  private readonly router = inject(Router);
  protected readonly painel = inject(PainelOrganizado);

  /** Na ordem escolhida. */
  protected readonly blocosDoPainel = computed(() => {
    return this.painel
      .blocos()
      .map((bloco) => ({
        ...bloco,
        nome: BLOCOS_DO_PAINEL.find((b) => b.id === bloco.id)?.nome ?? bloco.id,
      }));
  });

  /** Bloco que esta sendo arrastado no Organizar o Painel. */
  protected readonly arrastando = signal<IdDoBloco | null>(null);
  private readonly listaDoPainel = viewChild<ElementRef<HTMLElement>>('listaDoPainel');
  private pararDeArrastar = (): void => undefined;

  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly contas = signal<Usuario[]>([]);
  protected readonly carregandoAdmin = signal(false);

  protected readonly perfis: PigguRole[] = ['ADMIN', 'TITULAR', 'PARCEIRO', 'MEMBRO'];

  private readonly finance = inject(FinanceService);
  protected readonly moedas = signal<MoedaDisponivel[]>([]);
  protected readonly moeda = signal(this.auth.usuario()?.preferencias?.moeda ?? 'EUR');
  /** Uma ponta da cotacao do topo; conta antiga, sem ela, usa a moeda do app. */
  protected readonly moedaCotacao = signal(
    this.auth.usuario()?.preferencias?.moedaCotacao ?? this.moeda(),
  );
  protected readonly moedaConversao = signal(
    this.auth.usuario()?.preferencias?.moedaConversao ?? 'BRL',
  );
  protected readonly mostrarCotacao = signal(
    this.auth.usuario()?.preferencias?.mostrarCotacao ?? true,
  );
  protected readonly salvandoPreferencias = signal(false);
  protected readonly celular = inject(AvisosNoCelular);
  protected readonly avisoPorEmail = signal(this.auth.usuario()?.avisos?.email ?? false);
  protected readonly avisoNoCelular = signal(this.auth.usuario()?.avisos?.push ?? false);
  protected readonly salvandoAvisos = signal(false);
  protected readonly pessoas = inject(PessoasDaFamilia);
  protected readonly mostrarTudoAoParceiro = signal(
    this.auth.usuario()?.compartilhamento?.mostraTudoAoParceiro ?? false,
  );
  protected readonly compartilharPorPadrao = signal(
    this.auth.usuario()?.compartilhamento?.compartilharPorPadrao ?? false,
  );
  protected readonly salvandoCompartilhamento = signal(false);

  protected readonly excluindo = signal(false);

  protected readonly senhaAtual = signal('');
  protected readonly novaSenha = signal('');
  protected readonly repitaSenha = signal('');
  protected readonly salvandoSenha = signal(false);
  /** Erro e aviso dentro do proprio cartao: o topo da pagina fica longe dali. */
  protected readonly erroDaSenha = signal('');
  protected readonly avisoDaSenha = signal('');

  protected readonly salvandoFoto = signal(false);
  protected readonly apelido = signal(this.auth.usuario()?.apelido ?? '');
  protected readonly salvandoApelido = signal(false);

  protected readonly reenviando = signal(false);

  /** Primeira letra do nome, no lugar da foto quando nao ha nenhuma. */
  protected readonly inicial = computed(() =>
    (this.auth.usuario()?.nome ?? '?').trim().charAt(0).toUpperCase(),
  );

  constructor() {
    this.finance.listarMoedas().subscribe({
      next: (moedas) => this.moedas.set(moedas),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
    if (this.auth.ehAdmin()) {
      this.carregarAdmin();
    }
    inject(DestroyRef).onDestroy(() => this.pararDeArrastar());
  }

  /**
   * Comeca a arrastar um bloco: o mouse pega a linha toda, o dedo so o pontilhado. Os
   * movimentos sao ouvidos na janela, e nao no item, porque o item troca de lugar na
   * lista enquanto anda.
   */
  protected comecarArrastar(evento: PointerEvent, id: IdDoBloco): void {
    const alvo = evento.target as HTMLElement;
    const pelaAlca = alvo.closest('.alca') !== null;
    const peloMouse = evento.pointerType === 'mouse' && !alvo.closest('input');
    if (evento.button !== 0 || !(pelaAlca || peloMouse)) {
      return;
    }
    evento.preventDefault();
    this.pararDeArrastar();
    this.arrastando.set(id);
    const mover = (e: PointerEvent): void => this.arrastarAte(id, e.clientY);
    const soltar = (): void => this.pararDeArrastar();
    window.addEventListener('pointermove', mover);
    window.addEventListener('pointerup', soltar);
    window.addEventListener('pointercancel', soltar);
    this.pararDeArrastar = () => {
      window.removeEventListener('pointermove', mover);
      window.removeEventListener('pointerup', soltar);
      window.removeEventListener('pointercancel', soltar);
      this.arrastando.set(null);
      this.pararDeArrastar = () => undefined;
    };
  }

  /** O bloco vai para depois de todos os outros cujo meio ja ficou acima do ponteiro. */
  private arrastarAte(id: IdDoBloco, y: number): void {
    const linhas = [...(this.listaDoPainel()?.nativeElement.children ?? [])] as HTMLElement[];
    const acima = linhas.filter((linha) => {
      if (linha.dataset['bloco'] === id) {
        return false;
      }
      const caixa = linha.getBoundingClientRect();
      return caixa.top + caixa.height / 2 < y;
    }).length;
    this.painel.moverPara(id, acima);
  }

  /** Setas do teclado na alca; o foco acompanha o bloco no novo lugar. */
  protected moverPeloTeclado(evento: Event, id: IdDoBloco, passo: -1 | 1): void {
    evento.preventDefault();
    this.painel.mover(id, passo);
    setTimeout(() =>
      this.listaDoPainel()
        ?.nativeElement.querySelector<HTMLElement>(`[data-bloco="${id}"] .alca`)
        ?.focus(),
    );
  }

  /** Manda outro link de confirmacao do e-mail; o aviso aparece no cartao da senha. */
  protected async reenviarConfirmacao(): Promise<void> {
    this.erroDaSenha.set('');
    this.avisoDaSenha.set('');
    this.reenviando.set(true);
    try {
      await this.auth.reenviarConfirmacao();
      this.avisoDaSenha.set($localize`Mandamos um link novo. Ele vale por 24 horas.`);
    } catch (falha) {
      this.erroDaSenha.set(mensagemDeErro(falha));
    } finally {
      this.reenviando.set(false);
    }
  }

  /** Cria a senha (conta que so entrava pelo Google) ou troca a atual. */
  protected async salvarSenha(): Promise<void> {
    const pessoa = this.auth.usuario();
    this.erroDaSenha.set('');
    this.avisoDaSenha.set('');
    const problema =
      (pessoa?.temSenha && !this.senhaAtual() ? $localize`Informe a senha atual.` : '') ||
      problemaDaSenha(this.novaSenha(), pessoa?.email) ||
      (this.novaSenha() !== this.repitaSenha() ? $localize`As duas senhas nao sao iguais.` : '');
    if (problema) {
      this.erroDaSenha.set(problema);
      return;
    }
    const trocando = pessoa?.temSenha === true;
    this.salvandoSenha.set(true);
    try {
      await this.auth.definirSenha(this.novaSenha(), trocando ? this.senhaAtual() : undefined);
      this.senhaAtual.set('');
      this.novaSenha.set('');
      this.repitaSenha.set('');
      this.avisoDaSenha.set(
        trocando
          ? $localize`Senha trocada. Os outros aparelhos vão pedir para entrar de novo.`
          : $localize`Senha criada. Agora você também entra com e-mail e senha.`,
      );
    } catch (falha) {
      this.erroDaSenha.set(mensagemDeErro(falha));
    } finally {
      this.salvandoSenha.set(false);
    }
  }

  /**
   * Grava a cada troca, sem botao. Nao ha recado de sucesso: a cotacao do topo aparece ou
   * some na hora, e um aviso no alto da pagina empurraria a tela.
   */
  protected salvarPreferencias(): void {
    this.salvandoPreferencias.set(true);
    this.erro.set('');
    this.aviso.set('');
    this.users
      .salvarPreferencias({
        moeda: this.moeda(),
        moedaCotacao: this.moedaCotacao(),
        moedaConversao: this.moedaConversao(),
        mostrarCotacao: this.mostrarCotacao(),
      })
      .subscribe({
        next: (usuario) => {
          this.auth.atualizarUsuario(usuario);
          this.salvandoPreferencias.set(false);
        },
        error: (falha) => {
          // Os campos voltam para o que esta gravado: nao podem mostrar o que nao salvou.
          const gravadas = this.auth.usuario()?.preferencias;
          this.moeda.set(gravadas?.moeda ?? 'EUR');
          this.moedaCotacao.set(gravadas?.moedaCotacao ?? this.moeda());
          this.moedaConversao.set(gravadas?.moedaConversao ?? 'BRL');
          this.mostrarCotacao.set(gravadas?.mostrarCotacao ?? true);
          this.erro.set(mensagemDeErro(falha));
          this.salvandoPreferencias.set(false);
        },
      });
  }

  /** Le a foto escolhida, recorta e reduz no aparelho e envia. */
  protected async escolherFoto(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    if (!arquivo) {
      return;
    }
    this.erro.set('');
    this.aviso.set('');
    this.salvandoFoto.set(true);
    let base64: string;
    try {
      base64 = (await prepararFotoDePerfil(arquivo)).base64;
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, $localize`Não consegui ler essa imagem.`));
      this.salvandoFoto.set(false);
      return;
    }
    this.gravarFoto(this.users.trocarFoto(base64), $localize`Foto trocada.`);
  }

  protected removerFoto(): void {
    this.erro.set('');
    this.aviso.set('');
    this.salvandoFoto.set(true);
    this.gravarFoto(this.users.removerFoto(), $localize`Foto removida.`);
  }

  protected usarFotoDoGoogle(): void {
    this.erro.set('');
    this.aviso.set('');
    this.salvandoFoto.set(true);
    this.gravarFoto(this.users.usarFotoDoGoogle(), $localize`Voltamos para a foto do Google.`);
  }

  private gravarFoto(pedido: Observable<Usuario>, sucesso: string): void {
    pedido.subscribe({
      next: (usuario) => {
        this.auth.atualizarUsuario(usuario);
        this.aviso.set(sucesso);
        this.salvandoFoto.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvandoFoto.set(false);
      },
    });
  }

  protected async salvarApelido(): Promise<void> {
    if (!this.apelido().trim()) {
      this.erro.set($localize`Conte como podemos te chamar.`);
      return;
    }
    this.salvandoApelido.set(true);
    this.erro.set('');
    this.aviso.set('');
    try {
      this.auth.atualizarUsuario(await firstValueFrom(this.users.sobreVoce(this.apelido().trim())));
      this.aviso.set($localize`Nome salvo.`);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
    } finally {
      this.salvandoApelido.set(false);
    }
  }

  /** No celular, a permissao do sistema vem antes de gravar: sem ela o push fica desligado. */
  protected async salvarAvisos(): Promise<void> {
    this.salvandoAvisos.set(true);
    this.erro.set('');
    this.aviso.set('');
    let push = this.avisoNoCelular() && this.celular.disponivel;
    if (push && !(await this.celular.ativar())) {
      push = false;
      this.avisoNoCelular.set(false);
      this.erro.set(
        $localize`Permita as notificações do Piggu nos Ajustes do iPhone e tente de novo.`,
      );
    }
    this.users.salvarAvisos(this.avisoPorEmail(), push).subscribe({
      next: (usuario) => {
        this.auth.atualizarUsuario(usuario);
        if (!this.erro()) {
          this.aviso.set($localize`Avisos salvos.`);
        }
        this.salvandoAvisos.set(false);
      },
      error: (falha) => {
        // A chave volta para o que esta gravado: nao pode ficar ligada sem ter salvo.
        this.avisoPorEmail.set(this.auth.usuario()?.avisos?.email ?? false);
        this.avisoNoCelular.set(this.auth.usuario()?.avisos?.push ?? false);
        this.erro.set(mensagemDeErro(falha));
        this.salvandoAvisos.set(false);
      },
    });
  }

  /** Cada pessoa decide o que o parceiro ve; a mudanca vale para ele no proximo login ou em minutos. */
  protected salvarCompartilhamento(): void {
    this.salvandoCompartilhamento.set(true);
    this.erro.set('');
    this.aviso.set('');
    this.users
      .salvarCompartilhamento({
        mostraTudoAoParceiro: this.mostrarTudoAoParceiro() && this.pessoas.parceiro() !== null,
        compartilharPorPadrao: this.compartilharPorPadrao(),
      })
      .subscribe({
        next: (usuario) => {
          this.auth.atualizarUsuario(usuario);
          this.aviso.set($localize`Compartilhamento salvo.`);
          this.salvandoCompartilhamento.set(false);
        },
        error: (falha) => {
          const salvo = this.auth.usuario()?.compartilhamento;
          this.mostrarTudoAoParceiro.set(salvo?.mostraTudoAoParceiro ?? false);
          this.compartilharPorPadrao.set(salvo?.compartilharPorPadrao ?? false);
          this.erro.set(mensagemDeErro(falha));
          this.salvandoCompartilhamento.set(false);
        },
      });
  }

  protected baixarMeusDados(): void {
    this.erro.set('');
    this.users.exportarMeusDados().subscribe({
      next: (arquivo) => baixar(arquivo, 'piggu-meus-dados.json'),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluirConta(): void {
    const texto =
      $localize`Excluir sua conta apaga tudo o que você lançou. Se você for a última pessoa da família, ` +
      $localize`tudo da família é apagado. Se outras pessoas ficarem, as categorias que você criou ficam com ` +
      $localize`elas, sem o seu nome. Uma assinatura Premium feita por você é cancelada. Isso não tem volta. ` +
      $localize`Digite EXCLUIR para confirmar.`;
    if (prompt(texto)?.trim().toUpperCase() !== 'EXCLUIR') {
      return;
    }
    this.excluindo.set(true);
    this.users.excluirConta().subscribe({
      next: () => void this.auth.encerrarLocalmente(),
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.excluindo.set(false);
      },
    });
  }

  protected escolherModo(modo: ModoDeAparencia): void {
    this.temasDoApp.escolherModo(modo);
  }

  protected escolherTema(id: IdDoTema): void {
    if (!this.temasDoApp.escolher(id)) {
      this.convidarParaOPremium();
    }
  }

  protected async escolherIcone(id: IdDoIcone): Promise<void> {
    if (!(await this.iconesDoApp.escolher(id))) {
      this.convidarParaOPremium();
    }
  }

  protected dispensarAvisoDoPadrao(): void {
    esquecerQueVoltouAoPadrao();
    this.voltouAoPadrao.set(false);
  }

  /** O titular vai para os planos; quem nao assina pela familia recebe o recado. */
  private convidarParaOPremium(): void {
    if (this.auth.ehTitular()) {
      void this.router.navigate(['/plano']);
    } else {
      this.aviso.set(
        $localize`Essa personalização é do Piggu Premium. Quem cuida da assinatura é o titular da família.`,
      );
    }
  }

  protected sair(): void {
    void this.auth.sair();
  }

  protected alterarPerfil(conta: Usuario, role: PigguRole): void {
    this.users.alterar(conta.id, { role }).subscribe({
      next: () => this.carregarAdmin(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alternarAtivo(conta: Usuario): void {
    this.users.alterar(conta.id, { ativo: !conta.ativo }).subscribe({
      next: () => this.carregarAdmin(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregarAdmin(): void {
    this.carregandoAdmin.set(true);
    this.users.listar().subscribe({
      next: (contas) => {
        this.contas.set(contas);
        this.carregandoAdmin.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregandoAdmin.set(false);
      },
    });
  }
}
