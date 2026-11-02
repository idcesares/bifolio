# Release checklist

v1.0 ships as source on GitHub (owner decision 2026-11-01). There is no store listing, no signed APK and no upload key in this release. Items marked **agent** can be done in a session; the rest need Isaac.

## 1. v1.0.0 on GitHub

| # | Item | Who | State |
|---|---|---|---|
| 1 | Device checks: PRF-02 to PRF-05, TalkBack, 200% font on the foldable, hardware keyboard, RTL, math checklist | Isaac | Owner sign-off 2026-11-01, no problems found, numbers not recorded (see `docs/perf/M6.md`) |
| 2 | Policy checks (`checkPolicy`): no INTERNET (PRV-01), `allowBackup="false"` (PRV-05) | agent | Green in CI on the release commit |
| 3 | Folder grants list with revoke in settings (PRV-04) | agent | Done |
| 4 | README, LICENSE, NOTICE, PROVENANCE, `docs/PRIVACY.md` current | agent | Checked 2026-11-01 |
| 5 | Secrets scan of the tree and the full history (keystores, passwords, `local.properties`) | agent | Done 2026-11-01: no keystore or secret in any commit; only the environment variable names appear in the build script |
| 6 | Run `./gradlew verifyRoborazziDebug` once on a machine with JDK 21 | Isaac | The screenshot gate is not in CI and had not run since the last UI commits when this list was written |
| 7 | Make the repository public: `gh repo edit idcesares/bifolio --visibility public` | Isaac | Note: every commit carries the author address in the git history, and it becomes public with the repository |
| 8 | Tag `v1.0.0` and create the GitHub release with notes | Isaac | Source only, no APK attached |

Suggested release notes: "First version. Read-only Markdown reader with book typography and two-page spreads on foldables. No network permission, no accounts, no tracking. Source only: build it with `./gradlew :app:assembleDebug` (see `docs/ENVIRONMENT.md`)."

## 2. If the app is ever published on a store

Not part of v1.0. Kept here so the work is not lost.

### Before the store

| # | Item | Who |
|---|---|---|
| 1 | Upload keystore generated and backed up outside the repository; `BIFOLIO_KEYSTORE`, `BIFOLIO_KEYSTORE_PASSWORD`, `BIFOLIO_KEY_ALIAS`, `BIFOLIO_KEY_PASSWORD` set in the environment or `local.properties` (never committed) | Isaac |
| 2 | Signed release build per ABI: `./gradlew :app:assembleRelease`, verify with `apksigner verify --print-certs` | agent |
| 3 | Measured numbers for PRF-02 to PRF-05 in `docs/perf/M6.md` | Isaac |
| 4 | A week of real reading | Isaac |

Whatever key signs a public APK becomes the update key for everyone who installs it. Decide it once.

### Play Console

| # | Item | Notes |
|---|---|---|
| 1 | Developer account and app entry | Package `dev.dcesares.bifolio` |
| 2 | Privacy policy URL | Publish `docs/PRIVACY.md` at a public URL (for example GitHub Pages). Contact: bifolio@dcesares.dev |
| 3 | Data safety form | "No data collected, no data shared" (PRV-06). Matches: no INTERNET permission, no SDKs, no backup |
| 4 | App access, ads, content rating | No login needed, no ads. Fill the content rating questionnaire |
| 5 | Listing assets | Icon 512 px, feature graphic 1024x500, phone screenshots, foldable and tablet screenshots (spread, fold continuity), the unfold video |
| 6 | Upload the AAB | Enroll in Play App Signing; the keystore above is the upload key |
| 7 | Closed test | Check the current Play rules for your account type (minimum testers and duration) before counting days |
| 8 | Production release | Isaac presses publish |

Build the bundle with `./gradlew :app:bundleRelease`. Per-ABI APK splits are for sideloading and local checks; Play takes the AAB.

### Listing text (PT-BR draft)

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
