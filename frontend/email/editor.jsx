import { Editor } from '@maily-to/core';
import { VariableExtension, PlaceholderExtension } from '@maily-to/core/extensions';
import {
  blockquote, bulletList, button, columns, divider, footer, heading1, heading2, heading3,
  image, logo, orderedList, section, spacer, text
} from '@maily-to/core/blocks';
import { Maily } from '@maily-to/render';
import { createRoot } from 'react-dom/client';
import React from 'react';

const traduzir = (bloco, title, description, ...termos) => ({
  ...bloco, title, description, searchTerms: [...termos, ...bloco.searchTerms]
});

const blocos = [
  {
    title: 'Texto',
    commands: [
      traduzir(text, 'Parágrafo', 'Escrever texto', 'texto'),
      traduzir(heading1, 'Título', 'Título principal', 'título', 'titulo'),
      traduzir(heading2, 'Subtítulo', 'Título de seção', 'subtitulo'),
      traduzir(heading3, 'Título menor', 'Título de apoio', 'titulo'),
      traduzir(bulletList, 'Lista com marcadores', 'Itens sem numeração', 'lista'),
      traduzir(orderedList, 'Lista numerada', 'Itens em ordem', 'lista'),
      traduzir(blockquote, 'Citação', 'Destacar um trecho', 'citacao'),
      traduzir(footer, 'Rodapé', 'Texto de rodapé', 'rodape')
    ]
  },
  {
    title: 'Composição',
    commands: [
      traduzir(button, 'Botão', 'Adicionar uma ação com link', 'botao', 'link'),
      traduzir(image, 'Imagem', 'Inserir imagem por URL', 'imagem'),
      traduzir(logo, 'Logotipo', 'Inserir a marca por URL', 'marca'),
      traduzir(divider, 'Divisor', 'Separar trechos', 'divisor'),
      traduzir(spacer, 'Espaçamento', 'Criar espaço entre blocos', 'espaco'),
      traduzir(section, 'Seção', 'Agrupar conteúdo', 'secao'),
      traduzir(columns, 'Colunas', 'Organizar conteúdo lado a lado', 'colunas')
    ]
  }
];

function converterVariaveis(bloco, variaveis) {
  if (bloco.type !== 'text') {
    return [{ ...bloco, ...(bloco.content && {
      content: bloco.content.flatMap(filho => converterVariaveis(filho, variaveis))
    }) }];
  }
  return bloco.text.split(/(\{\{[a-z_]+}})/).filter(Boolean).map(trecho => {
    const variavel = trecho.startsWith('{{') && trecho.endsWith('}}')
      ? variaveis.find(item => item.name === trecho.slice(2, -2)) : null;
    return variavel ? {
      type: 'variable',
      attrs: { id: variavel.name, label: variavel.label },
      ...(bloco.marks && { marks: bloco.marks })
    } : { ...bloco, text: trecho };
  });
}

export function montar(elemento, variaveis, aoEditar) {
  const raiz = createRoot(elemento);
  let editor;
  let concluir;
  let htmlLegado;
  const pronto = new Promise(resolve => { concluir = resolve; });
  const extensoes = [
    VariableExtension.configure({
      variables: variaveis,
      disableInput: true,
      renderLabel: ({ node }) => variaveis.find(({ name }) => name === node.attrs.id)?.label ?? node.attrs.id
    }),
    PlaceholderExtension.configure({ placeholder: 'Escreva ou digite / para escolher um bloco' })
  ];
  raiz.render(<Editor
    onCreate={instancia => {
      editor = instancia;
      editor.view.dom.setAttribute('aria-label', 'Corpo do e-mail');
      editor.view.dom.setAttribute('role', 'textbox');
      editor.view.dom.setAttribute('aria-multiline', 'true');
      concluir(editor);
    }}
    onUpdate={() => {
      htmlLegado = undefined;
      aoEditar?.();
    }}
    extensions={extensoes}
    blocks={blocos}
    config={{
      hasMenuBar: false,
      spellCheck: true,
      autofocus: false,
      wrapClassName: 'firma-maily',
      contentClassName: 'firma-maily-conteudo',
      bodyClassName: 'firma-maily-documento'
    }}
  />);
  return {
    pronto,
    desmontar: () => raiz.unmount(),
    carregar(conteudo, htmlAnterior) {
      htmlLegado = htmlAnterior;
      if (conteudo) {
        try {
          if (editor.commands.setContent(conteudo, false, { errorOnInvalidContent: true })) {
            htmlLegado = undefined;
            return;
          }
        } catch {
          // O HTML salvo continua disponível se o esquema de edição mudar.
        }
      }
      editor.commands.setContent(htmlAnterior ?? '', false);
      if (htmlAnterior?.includes('{{')) {
        const [comVariaveis] = converterVariaveis(editor.getJSON(), variaveis);
        editor.commands.setContent(comVariaveis, false);
      }
    },
    formatar(comando, valor) {
      editor.chain().focus()[comando](valor).run();
    },
    abrirBlocos() {
      const { $to } = editor.state.selection;
      if ($to.parent.type.name === 'paragraph' && !$to.parent.content.size) {
        editor.chain().focus().insertContent('/').run();
      } else {
        editor.chain().focus().insertContentAt($to.depth ? $to.after(1) : $to.pos, {
          type: 'paragraph',
          content: [{ type: 'text', text: '/' }]
        }).run();
      }
    },
    inserirVariavel(id) {
      editor.chain().focus().insertContent({ type: 'variable', attrs: { id, label: id } }).run();
    },
    async exportar() {
      if (htmlLegado !== undefined) return { documento: null, html: htmlLegado };
      const documento = editor.getJSON();
      const maily = new Maily(documento);
      maily.setVariableFormatter(({ variable }) => '{{' + variable + '}}');
      maily.setHtmlProps({ lang: 'pt-BR' });
      maily.setTheme({ font: { fontFamily: 'Arial', fallbackFontFamily: 'sans-serif', webFont: undefined } });
      const html = await maily.render();
      return { documento, html };
    }
  };
}
