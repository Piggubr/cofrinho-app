#!/usr/bin/env python3
"""Copia os Termos de Uso e o Aviso de Privacidade do site para o app.

Le frontend/src/app/features/legal/{termos,privacidade}.html e a versao e o contato de
core/privacidade/aviso.ts, e grava Piggu/Recursos/{Termos,Privacidade}.md, que o app mostra
nas telas nativas (TextoLegalView). O CI roda este script e falha se o texto do app ficou
diferente do site: mudou o HTML, rode de novo e faca o commit.

    python3 ios-nativo/scripts/textos-legais.py
"""
import html
import re
from html.parser import HTMLParser
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
LEGAL = RAIZ / "frontend/src/app/features/legal"
AVISO = RAIZ / "frontend/src/app/core/privacidade/aviso.ts"
DESTINO = RAIZ / "ios-nativo/Piggu/Recursos"


class Conversor(HTMLParser):
    """HTML do Angular para o Markdown curto que o app entende: #, ##, -, | e paragrafos."""

    def __init__(self, valores):
        super().__init__(convert_charrefs=True)
        self.valores = valores
        self.blocos = []
        self.texto = []
        self.link = None
        self.tabela = None
        self.linha = None
        self.pular = 0

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if self.pular or tag == "button":
            self.pular += 1
            return
        if tag == "a":
            destino = attrs.get("routerlink") or attrs.get("[href]") or ""
            if destino == "/entrar":
                # "← Voltar": no app e o botao de voltar da navegacao.
                self.pular += 1
                return
            if "mailto" in destino:
                destino = "mailto:" + self.valores["contato"]
            else:
                destino = "piggu:/" + destino
            self.link = destino
            self.texto.append("[")
        elif tag == "strong":
            self.texto.append("**")
        elif tag == "table":
            self.tabela = []
        elif tag == "tr":
            self.linha = []
        elif tag in ("td", "th"):
            self.texto = []

    def handle_endtag(self, tag):
        if self.pular:
            self.pular -= 1
            return
        if tag == "a":
            self.texto.append("](%s)" % self.link)
            self.link = None
        elif tag == "strong":
            self.texto.append("**")
        elif tag in ("td", "th"):
            self.linha.append(self.limpar())
        elif tag == "tr":
            self.tabela.append(self.linha)
        elif tag == "table":
            for indice, linha in enumerate(self.tabela):
                self.blocos.append(("|#" if indice == 0 else "|") + " | ".join(linha))
            self.tabela = None
        elif tag == "h1":
            self.blocos.append("# " + self.limpar())
        elif tag == "h2":
            self.blocos.append("## " + self.limpar())
        elif tag == "li":
            self.blocos.append("- " + self.limpar())
        elif tag == "p":
            texto = self.limpar()
            if texto:
                self.blocos.append(texto)

    def handle_data(self, dados):
        if self.pular or dados.strip().startswith("@"):
            return
        self.texto.append(dados)

    def limpar(self):
        texto = re.sub(r"\s+", " ", "".join(self.texto)).strip()
        texto = re.sub(r"\{\{\s*(\w+)\s*\}\}", lambda m: self.valores[m.group(1)], texto)
        texto = texto.replace("[ ", "[").replace(" ](", "](")
        self.texto = []
        return html.unescape(texto)


def main():
    aviso = AVISO.read_text(encoding="utf-8")
    valores = {
        "versao": re.search(r"VERSAO_DO_AVISO = '([^']+)'", aviso).group(1),
        "contato": re.search(r"CONTATO_PRIVACIDADE = '([^']+)'", aviso).group(1),
    }
    for origem, destino in (("termos.html", "Termos.md"), ("privacidade.html", "Privacidade.md")):
        conversor = Conversor(valores)
        conversor.feed((LEGAL / origem).read_text(encoding="utf-8"))
        cabecalho = "<!-- Gerado por ios-nativo/scripts/textos-legais.py a partir do site. Nao edite. -->"
        (DESTINO / destino).write_text(cabecalho + "\n\n" + "\n\n".join(conversor.blocos) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
