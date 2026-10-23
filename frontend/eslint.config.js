// @ts-check
// Lint do front (npm run lint), conferido no CI pelo job "Lint". As regras sao as
// recomendadas do ESLint, do typescript-eslint e do angular-eslint, com acessibilidade
// nos templates; as excecoes abaixo dizem o porque.
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
      // Parametro com _ na frente e de proposito: a assinatura precisa dele, o corpo nao.
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // () => {} e o "nao faz nada" de callback vazio (ControlValueAccessor, subscribe).
      '@typescript-eslint/no-empty-function': ['error', { allow: ['arrowFunctions'] }],
    },
  },
  {
    // Nos testes e nos dublês, funcao vazia e o jeito de dizer "nao faz nada".
    files: ['**/*.spec.ts', 'src/app/testing/**/*.ts'],
    rules: {
      '@typescript-eslint/no-empty-function': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
