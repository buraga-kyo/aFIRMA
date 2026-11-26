import { createRequire } from 'node:module';
import { readFile, writeFile } from 'node:fs/promises';
import { build } from 'esbuild';
import postcss from 'postcss';

const require = createRequire(import.meta.url);
const caminhoCss = require.resolve('@maily-to/core/style.css');
const destino = '../../src/main/resources/static/js/vendor';
const css = postcss.parse(await readFile(caminhoCss, 'utf8'));
// O Trator já fornece a base tipográfica e o reset de elementos.
css.walkAtRules('layer', regra => {
  if (regra.params === 'base' && regra.nodes) regra.remove();
});
css.walkComments(comentario => {
  if (comentario.text.startsWith('# sourceMappingURL')) comentario.remove();
});
await build({
  entryPoints: ['editor.jsx'],
  bundle: true,
  format: 'iife',
  globalName: 'FirmaEditorEmail',
  minify: true,
  legalComments: 'eof',
  outfile: destino + '/maily.js',
  define: { 'process.env.NODE_ENV': '"production"' }
});
await writeFile(destino + '/maily.css', css.toString());
