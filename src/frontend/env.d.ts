/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_NAME: string
  readonly VITE_PORT: string
  readonly VITE_ROOT_API: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}