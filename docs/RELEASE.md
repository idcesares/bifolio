# Release checklist (M7)

Everything the owner does to ship v1.0. Items marked **agent** can be done in a session; the rest need Isaac.

## 1. Before the store

| # | Item | Who | Done when |
|---|---|---|---|
| 1 | Device measurements PRF-02 to PRF-05 | Isaac | Numbers recorded in `docs/perf/M6.md`, all inside budget (SPEC performance section) |
| 2 | TalkBack, 200% font on the foldable, hardware keyboard, RTL | Isaac | A11Y-01 moves from "pending device" to done |
| 3 | A week of real reading (M6 checkpoint) | Isaac | No data loss, no lost position |
| 4 | Upload keystore generated and backed up outside the repository | Isaac | `BIFOLIO_KEYSTORE`, `BIFOLIO_KEYSTORE_PASSWORD`, `BIFOLIO_KEY_ALIAS`, `BIFOLIO_KEY_PASSWORD` set in the environment or `local.properties` (never committed) |
| 5 | Signed release build per ABI | agent | `./gradlew :app:assembleRelease` with the key set; verify with `apksigner verify --print-certs` |
| 6 | Policy checks on the release APK | agent | `checkPolicy` green; merged manifest has no INTERNET (PRV-01) and `allowBackup="false"` (PRV-05) |
| 7 | Folder grants list with revoke in settings (PRV-04) | agent | Screen and tests merged |

## 2. Play Console

| # | Item | Notes |
|---|---|---|
| 1 | Developer account and app entry | Package `dev.dcesares.bifolio` |
| 2 | Privacy policy URL | Publish `docs/PRIVACY.md` at a public URL (for example GitHub Pages once the repository is public). Contact: bifolio@dcesares.dev |
| 3 | Data safety form | "No data collected, no data shared" (PRV-06). Matches: no INTERNET permission, no SDKs, no backup |
| 4 | App access, ads, content rating | No login needed, no ads. Fill the content rating questionnaire |
| 5 | Listing assets | Icon 512 px, feature graphic 1024x500, phone screenshots, foldable and tablet screenshots (spread, fold continuity), the unfold video |
| 6 | Upload the AAB | Enroll in Play App Signing; the keystore from 1.4 is the upload key |
| 7 | Closed test | Check the current Play rules for your account type (minimum testers and duration) before counting days |
| 8 | Production release | Isaac presses publish |

Build the bundle with `./gradlew :app:bundleRelease`. Per-ABI APK splits are for sideloading and local checks; Play takes the AAB.

## 3. Repository

| # | Item | Who |
|---|---|---|
| 1 | README, LICENSE, NOTICE, PROVENANCE present and current | agent (checked 2026-10-25) |
| 2 | Scan history and files for secrets (keystore, passwords, `local.properties`) before going public | agent |
| 3 | Make the repository public | Isaac |
| 4 | Tag `v1.0.0` and attach the release notes | Isaac |

## 4. Listing text (PT-BR draft)

**Nome:** Bifolio

**Descrição curta (até 80 caracteres):**
Leitor de Markdown que diagrama como livro e abre duas páginas no dobrável.

**Descrição completa:**

Bifolio lê arquivos Markdown como se fossem um livro.

O texto é diagramado com medida de linha confortável, hifenização e paginação cuidadosa, sem linhas viúvas ou órfãs. Em um celular dobrável aberto, o app mostra duas páginas lado a lado, com a lombada exatamente sobre a dobra. Ao dobrar ou desdobrar, você continua no mesmo ponto do texto.

O que ele faz:
- Leitura paginada ou em rolagem
- Quatro temas de leitura
- Sumário, busca e notas de rodapé em popover
- Fórmulas matemáticas e código com destaque de sintaxe
- Imagens locais, com acesso à pasta concedido por você
- Acessibilidade: TalkBack, tamanho de fonte, redução de movimento e navegação por teclado

Privacidade:
- Sem permissão de Internet, sem contas, sem anúncios, sem análise de uso
- Somente leitura: seus arquivos nunca são modificados
- Nada sai do aparelho

Código aberto sob licença Apache 2.0.

**Notas da versão 1.0:**
Primeira versão.
