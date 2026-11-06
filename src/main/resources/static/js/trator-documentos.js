/** Área de edição com folha e ferramentas. Não depende do trator de tabelas. */
(() => {
  class TratorDocumentos {
    constructor({ elemento, editor }) {
      this.raiz = document.querySelector(elemento);
      this.editor = this.raiz.querySelector(editor);
      this.prancheta = this.editor.querySelector('.registro-prancheta');
      this.escala = 1;
      this.eventos = new AbortController();
      window.addEventListener(
        'firma:editor-pagina',
        () => {
          if (this.ajusteAutomatico) this.encaixarFolha();
        },
        { signal: this.eventos.signal },
      );
      this.raiz.addEventListener(
        'click',
        (evento) => {
          const botao = evento.target.closest('button[data-documentos]');
          if (!botao || botao.disabled) return;
          const acao = botao.dataset.documentos;
          if (acao === 'aumentar') this.definirEscala(this.escala + 0.1);
          else if (acao === 'diminuir') this.definirEscala(this.escala - 0.1);
          else if (acao === 'encaixar') this.encaixarFolha();
          else {
            const destino = document.getElementById(botao.getAttribute('aria-controls'));
            destino.hidden = !destino.hidden;
            botao.setAttribute('aria-expanded', String(!destino.hidden));
            this.raiz.classList.toggle(
              'documentos-sem-ferramentas',
              this.raiz.querySelector('#documentos-ferramentas').hidden,
            );
            this.encaixarFolha();
          }
        },
        { signal: this.eventos.signal },
      );
      const atualizarAcesso = () => {
        const conectado = !this.editor.hidden;
        this.raiz.querySelector('#documentos-vazio').hidden = conectado;
        this.raiz.querySelector('.registro-publicar').hidden = !conectado;
        this.raiz
          .querySelectorAll('[data-documentos]')
          .forEach((botao) => (botao.disabled = !conectado));
        if (conectado) this.encaixarFolha();
      };
      this.observadorAcesso = new MutationObserver(atualizarAcesso);
      this.observadorAcesso.observe(this.editor, { attributes: true, attributeFilter: ['hidden'] });
      this.observadorLargura = new ResizeObserver(() => {
        if (this.ajusteAutomatico) this.encaixarFolha();
      });
      this.observadorLargura.observe(this.prancheta);
      atualizarAcesso();
    }

    definirEscala(valor, automatico = false) {
      this.ajusteAutomatico = automatico;
      this.escala = Math.max(0.25, Math.min(1.5, valor));
      this.raiz.style.setProperty('--documentos-escala', this.escala);
      this.raiz.querySelector('[data-documentos-escala]').textContent =
        Math.round(this.escala * 100) + '%';
    }

    encaixarFolha() {
      if (this.editor.hidden) return;
      const aspecto = this.editor
        .querySelector('#template-canvas')
        .style.aspectRatio.split('/')
        .map(Number);
      const altura =
        aspecto.length === 2 && aspecto[0] > 0 && aspecto[1] > 0
          ? (595 * aspecto[1]) / aspecto[0]
          : 842;
      this.definirEscala(
        Math.min(
          1,
          (this.prancheta.clientWidth - 48) / 595,
          (this.prancheta.clientHeight - 48) / altura,
        ),
        true,
      );
    }

    destruir() {
      this.eventos.abort();
      this.observadorAcesso.disconnect();
      this.observadorLargura.disconnect();
    }
  }
  window.TratorDocumentos = TratorDocumentos;
})();
