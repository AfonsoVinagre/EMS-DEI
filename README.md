# EMS — Exam Management System

Plataforma de gestão centralizada de exames nacionais, com
modelo de **correção cega e atómica**. Permite carregar provas em PDF, segmentá-las
em perguntas (fragmentos), distribuir os fragmentos equitativamente pelos
professores de cada disciplina, agregar as notas em nota final, disponibilizar as
provas aos alunos para consulta e gerir pedidos de revisão dentro de um prazo
rigoroso — tudo isto com controlo de acessos por papéis (RBAC) estrito.

> Exercício prático 168-174/2025 — DEI. Backend Spring Boot + Frontend Vue 3.

---

## Índice

1. [Stack tecnológica](#stack-tecnológica)
2. [Configuração e execução](#configuração-e-execução)
3. [Contas de demonstração (seed)](#contas-de-demonstração-seed)
4. [Arquitetura](#arquitetura)
5. [Modelo de dados](#modelo-de-dados)
6. [Controlo de acessos (RBAC)](#controlo-de-acessos-rbac)
7. [Workflow de avaliação — as 5 fases](#workflow-de-avaliação--as-5-fases)
8. [Funcionalidades adicionais (20 valores)](#funcionalidades-adicionais-20-valores)
9. [Decisões de desenho (partes não explicitadas no enunciado)](#decisões-de-desenho-partes-não-explicitadas-no-enunciado)
10. [Estrutura do repositório e builds](#estrutura-do-repositório-e-builds)

---

## Stack tecnológica

| Camada | Tecnologia |
|---|---|
| Backend | Spring Boot 3.2.5, Java 21, Apache PDFBox 2.0.31, Lombok, JWT (jjwt) |
| Base de dados | PostgreSQL 14 (contentor Docker) |
| Frontend | Vue 3.4 (Composition API, `<script setup>`), Vuetify 3.6, Pinia (+persistedstate), Vue Router 4, axios 1.7 |
| Charts | Chart.js 4 |
| Build frontend | Vite 6, TypeScript 5.4, vue-tsc 2 |
| Auth | JWT (HS256), token de 24h enviado no header `Authorization: Bearer …` |

---

## Configuração e execução

### Pré-requisitos

- **Docker** (para o PostgreSQL)
- **Java 21** + **Maven** (backend)
- **Node.js 20+** (frontend)

### 1. Base de dados (PostgreSQL via Docker)

O `docker-compose.yml` sobe um PostgreSQL 14 na porta **7654** (mapeada para o
5432 interno) com a base de dados `emsdb`:

```bash
cd src
docker compose up -d
```

> A ligação esperada pelo backend é
> `jdbc:postgresql://localhost:7654/emsdb` (user/pass `postgres`/`postgres`),
> configurada em `backend/src/main/resources/application.properties`.

### 2. Backend (porta 8080)

```bash
cd src/backend
mvn spring-boot:run
```

No arranque, o `DataInitializer` popula a base de dados com escolas, disciplinas,
utilizadores e associações professor↔escola↔disciplina (ver
[Contas de demonstração](#contas-de-demonstração-seed)). Quaisquer PDFs presentes
em `./data/exams` são convertidos automaticamente em exames fragmentados, prontos
para o fluxo lock → distribuir.

### 3. Frontend (porta 8081)

```bash
cd src/frontend
npm install
npm run dev
```

A aplicação fica disponível em **http://localhost:8081** e fala com o backend em
`http://localhost:8080` (`VITE_ROOT_API` em `src/frontend/.env`).

### Variáveis relevantes (`application.properties`)

| Propriedade | Default | Descrição |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `create` | Cria o schema a cada arranque (desenvolvimento); os dados são reiniciados, mas o seed é idempotente. |
| `ems.storage.exams-dir` | `./data/exams` | PDFs originais + origem de seed de exames. |
| `ems.storage.fragments-dir` | `./data/fragments` | PNGs por página (fragmentos). |
| `ems.storage.annotations-dir` | `./data/annotations` | PNGs anotados pelos professores (canvas). |
| `ems.revision.window-hours` | `48` | Duração da janela de revisão por exame. |
| `security.jwt.expiration-ms` | `86400000` | Validade do token (24h). |

---

## Contas de demonstração (seed)

O `DataInitializer` cumpre e ultrapassa o mínimo pedido pelo enunciado (2 escolas,
10 alunos, 3 professores, disciplinas base, 1 administrador).

**Escolas:** IST (Lisboa), FCT (Lisboa), Escola Demo.
**Disciplinas:** ALG, POO, FIS, MAT.

| Email | Papel | Escola | Password |
|---|---|---|---|
| `admin@ems.pt` | ADMINISTRATOR | — | `password123` |
| `admin@dei.tecnico.ulisboa.pt` | ADMINISTRATOR | — | `admin123` |
| `staff.ist@ems.pt` | SCHOOL_STAFF | IST | `password123` |
| `staff.fct@ems.pt` | SCHOOL_STAFF | FCT | `password123` |
| `ana.marques@ems.pt` | TEACHER | IST (ALG, MAT) | `password123` |
| `bruno.costa@ems.pt` | TEACHER | IST (POO) | `password123` |
| `carla.sousa@ems.pt` | TEACHER | FCT (ALG, FIS) | `password123` |
| `diogo.ramos@ems.pt` | TEACHER | FCT (MAT, POO) | `password123` |
| `ines.lopes@ems.pt` … `goncalo.pereira@ems.pt` | STUDENT | IST/FCT | `password123` |

São criados **10 alunos** (5 IST + 5 FCT), **4 professores** e **2+1 escolas**,
com **7 associações** professor↔escola↔disciplina. Todas as contas `@ems.pt`
usam a password `password123`.

---

## Arquitetura

### Backend — `pt.ulisboa.tecnico.rnl.dei.ems`

API REST organizada por subsistemas (cada um com `domain/`, `dto/`,
`repository/`, `service/`, `controller/`):

| Package | Responsabilidade |
|---|---|
| `person` | Pessoas (admin, staff, professor, aluno) + hashing BCrypt. |
| `school` / `subject` | Escolas e disciplinas (CRUD admin). |
| `teacherassignment` | Associações ternárias professor↔escola↔disciplina. |
| `exam` | Upload de PDF, segmentação server-side, distribuição, release, marca de água. |
| `fragment` | Fragmentos (perguntas): imagens, nota definitiva, anotação definitiva. |
| `correction` | Tarefas de correção atribuídas a professores; submissão de nota + anotação. |
| `consultation` | Pedidos individuais de consulta + aprovação/rejeição. |
| `revision` | Pedidos de revisão (janela por exame), itens, scheduler de fecho, re-distribuição. |
| `audit` | Registo de auditoria inviolável (hash-chain SHA-256). |
| `systemstate` | Singleton de estado global (uploads bloqueados/desbloqueados). |
| `auth` / `security` | Login JWT, `SecurityUtils.currentPerson()`, `RolePermissions`, `Permission`. |
| `exceptions` | `DEIException` + `ErrorMessage` (mapeadas a HTTP 400 pelo `CustomExceptionHandler`). |

### Frontend — `src/`

- **Stores Pinia**: `auth` (sessão/JWT, persistida), `appearance` (toasts globais, loading).
- **`RemoteService`**: cliente axios central com interceptores (JWT no request,
  unwrap `response.data`, tratamento de erros global).
- **Views** por papel: `dashboard/`, `audit/`, `people/`, `schools/`, `subjects/`,
  `teacherassignments/`, `exams/` (+ `ExamFragmentsView`), `corrections/`,
  `consultations/` (`MyExamsView` aluno, `ExamConsultationView`, `StaffConsultationView`),
  `statistics/`.
- **Router** com guards por `allowedRoles`/`permission`; redirecionamento pós-login
  por papel (admin→dashboard, staff→exams, teacher→corrections, student→my-exams).

---

## Modelo de dados

```
Person ──┬──< Exam >── Subject
         │      └── School
         │
         ├──< TeacherAssignment >── (School, Subject)
         │
         ├──< CorrectionTask >── Fragment ──< Exam
         │
         ├──< ConsultationRequest >── Exam
         │
         └──< RevisionRequest >──< RevisionItem >── Fragment

AuditLog (hash-chain)   ·   SystemState (singleton)
```

**Entidades principais:**

- **Person** — `id, name, email (unique), password (BCrypt), type, school?`
- **School** — `name, code, region`
- **Subject** — `name, code`
- **TeacherAssignment** — `teacher, school, subject` (ternária, única por combinação)
- **Exam** — `student, subject, school, pdfPath, status, finalGrade, releasedAt`
- **Fragment** — `exam, subject, pageNumber, imagePath, maxScore, definitiveGrade`
- **CorrectionTask** — `fragment, teacher, phase (PRIMARY|REVISION), status (ASSIGNED|SUBMITTED), grade, assignedAt, submittedAt, annotatedImagePath`
- **ConsultationRequest** — `exam, student, status (PENDING|APPROVED|REJECTED)`
- **RevisionRequest** — `exam, student, status, windowClosesAt` · **RevisionItem** — `fragment, justification`
- **SystemState** — singleton `examsLocked`
- **AuditLog** — `action, actorEmail, timestamp, payload, prevHash, entryHash` (cadeia SHA-256)

**Ciclo de vida do Exame:**

```
UPLOADED ──(admin bloqueia + distribui)──▶ DISTRIBUTED ──(todos os fragmentos corrigidos)──▶ CORRECTED ──(release)──▶ RELEASED
```

A nota final é calculada quando o último fragmento do exame é corrigido
(`maybeMarkExamCorrected`). A transição para `RELEASED` abre a janela de revisão
desse exame (48h).

---

## Controlo de acessos (RBAC)

`RolePermissions` é a fonte de verdade: cada `PersonType` mapeia para um
`Set<Permission>`. Os endpoints usam `@PreAuthorize("hasAuthority('X')")`;
o frontend replica o gate por `meta.allowedRoles`/`meta.permission` no router.

| Permissão | ADMIN | STAFF | TEACHER | STUDENT |
|---|:---:|:---:|:---:|:---:|
| CRUD Escolas/Disciplinas/Pessoas, Associações | ✓ | — | — | — |
| `EXAM_DISTRIBUTE`, `EXAM_LOCK`, `PERSON_IMPERSONATE`, `AUDIT_READ` | ✓ | — | — | — |
| `EXAM_CREATE` (upload), `EXAM_RELEASE`, `STATISTICS_READ` | ✓ | ✓ | — | — |
| `PERSON_READ`, `SCHOOL_READ`, `SUBJECT_READ`, `EXAM_READ` | ✓ | ✓ | ✓ | ✓ |
| `STATISTICS_READ` | ✓ | ✓ | ✓ | — |

O ADMINISTRADOR recebe `EnumSet.allOf(Permission.class)`. A correção é **cega**:
o professor vê apenas a imagem isolada do fragmento, sem identidade do aluno nem
restante exame.

---

## Workflow de avaliação — as 5 fases

1. **Digitalização e Separação** — o SCHOOL_STAFF faz upload do PDF; o backend
   segmenta-o **server-side** com PDFBox (cada página → um PNG = fragmento). O
   exame fica `UPLOADED`.
2. **Distribuição** — o ADMINISTRADOR bloqueia os uploads e distribui; os
   fragmentos de cada disciplina são repartidos **equitativamente** pelos
   professores dessa disciplina (round-robin balanceado). Exame → `DISTRIBUTED`.
3. **Correção Atómica e Agregação** — cada professor avalia os seus fragmentos e
   submete a nota (opcionalmente com anotação visual). O exame transita para
   `CORRECTED` só quando **todos** os fragmentos estão corrigidos; a nota final
   é então calculada. O SCHOOL_STAFF tem uma tabela filtrável com as notas finais
   da sua escola por disciplina (`StatisticsView`).
4. **Disponibilização e Consulta** — o SCHOOL_STAFF aprova pedidos individuais de
   consulta ou usa o **Bulk Release** (por disciplina). O aluno vê o PDF original
   lado a lado com as classificações por fragmento (e a versão anotada, quando
   exista). Exame → `RELEASED` (abre a janela de revisão).
5. **Revisão de Prova** — durante 48h após o release, o aluno seleciona
   perguntas e submete um pedido com justificação. Um `RevisionScheduler` fecha
   cada exame individualmente ao fim da janela, recolhe os fragmentos alvo e
   re-distribui (idealmente a professores diferentes) como tarefas `REVISION`;
   o revisor avalia a justificação e atribui a **nota definitiva**.

---

## Funcionalidades adicionais (20 valores)

Foram implementadas **4** das funcionalidades extra (o enunciado pede 4 para 20
valores), além do core de 14:

### 1. Dashboard Estatístico
Vista admin-only (`/dashboard`) com KPIs e 4 gráficos (Chart.js 4): média por
disciplina, progresso global da correção, volume de pedidos de revisão e carga
por professor. Agregação no cliente a partir dos endpoints existentes — zero
acoplamento extra ao backend.

### 2. Marcas de Água Dinâmicas
Em `GET /exams/{id}/pdf`, o backend estampa **on-the-fly** todas as páginas do
PDF com PDFBox: email diagonal semitransparente + rodapé `email · data · IP`.
O ficheiro original no disco **não** é tocado; há fallback aos bytes originais se
a marca falhar. Só o PDF completo — **nunca** os fragmentos — para não quebrar a
correção cega. Cada download é ainda registado no log de auditoria
(`PDF_ACCESS` com email + IP).

### 3. Registo de Auditoria Inviolável
Package `audit/` com `AuditLog` (snapshots imutáveis, sem `@ManyToOne`) e
**cadeia de hashes SHA-256**: `entryHash = sha256(prevHash || payload)`, com
Genesis = 64×"0". `verifyChain()` deteta adulteração. Ações auditadas:
submissão/alteração de nota (`GRADE_SUBMIT`, com nota anterior → nova),
upload/distribuição/release de exames, acesso a PDF, pedidos de consulta
(criar/aprovar/rejeitar/auto-aprovar) e pedidos/processamento de revisão. Vista
admin-only `/audit` com filtro e botão **Verificar integridade**
(chip verde/vermelho).

> Nota de implementação: o timestamp é truncado para segundos no `record` — o
> `timestamptz` do PostgreSQL trunca nanossegundos e o `verifyChain` falharia sem
> isto. O registo é atómico com a ação (transação `REQUIRED`).

### 4. Correção com Anotações (Canvas)
O professor corrige sobre um `<canvas>` em resolução nativa com ferramentas de
desenho (caneta, texto, vistos ✓/✗, 4 cores, 3 espessuras, undo, limpar). A
composição imagem + anotações é exportada via `canvas.toBlob` e **submetida
atomicamente com a nota** (multipart). O PNG composto fica em
`./data/annotations/{taskId}.png` (coluna `annotated_image_path` na
`CorrectionTask`).

- **Definitiva:** a anotação visível para o aluno é a da tarefa **definitiva**
  (REVISION submetida → senão PRIMARY submetida), espelhando exatamente a lógica
  da nota definitiva.
- **Consulta do aluno:** `ExamConsultationView` tenta a versão anotada
  (`GET /fragments/{id}/annotated-image`) e faz fallback à imagem crua se não
  houver anotação (silenciando o erro esperado).
- **Revisita do professor:** ao reabrir um fragmento submetido, vê a sua anotação
  em modo só-leitura (`GET /correction-tasks/{id}/annotation`).
- O canvas é same-origin (blob: da nossa origem) → não fica *tainted* → o
  `toBlob` funciona.

---

## Decisões de desenho (partes não explicitadas no enunciado)

O enunciado pede documentação das decisões tomadas por bom senso (linha 73).

- **Segmentação server-side no upload.** O enunciado descreve uma UI para
  "separar" o PDF; optei por segmentar **automaticamente no backend** com
  PDFBox (cada página → um fragmento PNG), eliminando o estado intermédio
  `SEGMENTED`. O *Cropping Interativo* (caixas delimitadoras) era uma
  funcionalidade extra candidata; não foi implementada — a separação por página
  cobre o fluxo core.
- **Distribuição equitativa** por round-robin balanceado sobre os professores da
  disciplina, garantindo que cada professor recebe fragmentos de **múltiplos
  exames** (não um exame inteiro), preservando a correção cega.
- **Correção cega garantida por desenho:** o professor acede apenas a
  `CorrectionTask` suas; nunca vê o PDF completo nem o nome do aluno. A marca de
  água aplica-se só ao PDF (fase de consulta), nunca aos fragmentos.
- **Nota e anotação definitivas = REVISION submetida → senão PRIMARY submetida**
  (`FragmentService.computeGrade` / `resolveDefinitiveAnnotatedImagePath`).
- **Janela de revisão por exame (48h)**, aberta no release e fechada
  automaticamente por um `RevisionScheduler` que corre de minuto a minuto; o
  fecho re-distribui os fragmentos alvo como tarefas `REVISION`.
- **Anotação = PNG composto no submit (burn-in).** O canvas contém sempre a
  composição imagem + traços; o `toBlob` dá o PNG final diretamente. Simples de
  servir e coerente com a semântica de tarefa submetida uma única vez (locked).
  Overlay vetorial editável pós-submit ficaria fora de âmbito.
- **Auditoria via hash-chain** (tamper-evident) em vez de log simples; snapshots
  imutáveis (sem relações JPA) para a cadeia ser autocontida e verificável.
- **`DEIException` → HTTP 400** (não 404) em todos os erros de domínio; o
  frontend faz fallback em qualquer non-2xx (ex.: ausência de anotação).
- **`ddl-auto=create`** em desenvolvimento: o schema recria-se a cada arranque,
  mas o `DataInitializer` usa *upserts* idempotentes (só insere o que falta), e
  re-seedeia exames a partir de PDFs em `./data/exams`.

---

## Estrutura do repositório e builds

```
dei-ems-afonso-vinagre/
├── README.md
├── enunciado.md
└── src/
    ├── docker-compose.yml        # PostgreSQL 14 (porta 7654)
    ├── backend/                  # Spring Boot 3.2.5 / Java 21
    │   ├── pom.xml
    │   └── src/main/java/pt/ulisboa/tecnico/rnl/dei/ems/
    │       └── <subsystems>      # person, school, subject, exam, fragment,
    │                             # correction, consultation, revision, audit,
    │                             # systemstate, auth, security, config, exceptions
    └── frontend/                 # Vue 3 + Vuetify 3 + Vite
        ├── package.json
        ├── vite.config.ts
        └── src/                  # views/, components/, stores/, services/, models/
```

### Verificar os builds

```bash
# Backend
cd src/backend
mvn compile

# Frontend (type-check vue-tsc + build Vite)
cd src/frontend
npm install
npm run build          # type-check + build-only
# ou, em desenvolvimento:
npm run dev
```

Ambos os builds passam sem erros. `npm run build` executa o type-check
(`vue-tsc --noEmit -p tsconfig.app.json`) e o build de produção Vite em paralelo.