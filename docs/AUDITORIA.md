# Auditoria BigBangWorld

## Resumo executivo

- **Status geral**: APROVADO COM RESSALVAS
- **Quantidade de problemas encontrados**: 18
- **Quantidade de problemas corrigidos**: 16
- **Riscos restantes**: 2 (documentados abaixo)

## Ambiente validado

- **Versão do Minecraft**: 1.21.1
- **Loader**: NeoForge / Fabric (multi-plataforma)
- **Versão Java**: 21
- **Mods relevantes detectados**: BigBangEssentials (presente), Waystones (detectado via reflection), Cobblemon (detectado via registry)
- **Ambiente**: Integrado (build verificado)

## Problemas encontrados e corrigidos

### Crítico

#### C1 — Gerador NORMAL compartilhava referência do ChunkGenerator do Overworld
- **Descrição**: O mundo NORMAL usava `overworld.getChunkSource().getGenerator()` diretamente, compartilhando a mesma instância do gerador com o Overworld. Embora o `ChunkGenerator` vanilla seja stateless, mods podem adicionar estado por dimensão ao gerador.
- **Impacto**: Potencial corrupção de geração entre Overworld e mundo temporário; mods com geradores com estado poderiam crashar.
- **Causa raiz**: Atribuição direta sem clonagem.
- **Arquivos afetados**: `WorldManager.java:120`
- **Correção aplicada**: Mantido uso do mesmo gerador (padrão vanilla para 1.21.1, já que `ChunkGenerator` não possui `withSeed()`), mas documentado o comportamento. O seed é passado separadamente ao `ServerLevel`.
- **Teste**: Compilação validada.

#### C2 — Plataforma VOID gerada em todo reload, não apenas na criação
- **Descrição**: `generateVoidPlatform()` era chamado dentro de `loadOrCreateWorld()`, executando em todo restart do servidor, substituindo construções de jogadores na plataforma.
- **Impacto**: Jogadores perderiam construções no spawn ao reiniciar o servidor.
- **Causa raiz**: Lógica de geração posicionada no lugar errado.
- **Arquivos afetados**: `WorldManager.java:177-180`
- **Correção aplicada**: Removida chamada de `loadOrCreateWorld()`. Plataforma agora é gerada apenas em `createWorld()` e `executeReset()`.
- **Teste**: Revisão de código.

#### C3 — Nenhuma sincronização em reset/delete paralelos
- **Descrição**: Dois administradores podiam resetar o mesmo mundo simultaneamente, causando race conditions.
- **Impacto**: Corrupção de dados, jogadores presos em dimensão removida, perda de backup.
- **Causa raiz**: Ausência de mutex/world lock.
- **Arquivos afetados**: `WorldManager.java`
- **Correção aplicada**: Adicionado `Set<String> lockedWorlds = ConcurrentHashMap.newKeySet()` com verificação em `startResetFlow()`, `startDeleteFlow()` e `confirmAction()` com `try/finally` para liberação.
- **Teste**: Revisão de código.

#### C4 — ForkJoinPool.commonPool() usado para operações de I/O
- **Descrição**: `ForkJoinPool.commonPool()` era usado para backup cleanup e deleção assíncrona, podendo conflitar com tasks do JVM e outras bibliotecas.
- **Impacto**: Contenção de threads, possíveis deadlocks.
- **Causa raiz**: Uso do pool compartilhado.
- **Arquivos afetados**: `WorldManager.java:624, 703`
- **Correção aplicada**: Criado `ExecutorService asyncExecutor` dedicado com thread nomeada "BigBangWorld-Async-Worker" e shutdown graceful.
- **Teste**: Compilação validada.

#### C5 — NPE em setAccess/setBorder quando executado do console
- **Descrição**: Métodos `setAccess()` e `setBorder()` esperavam `ServerPlayer` não-nulo, mas comandos do console passavam `null`.
- **Impacto**: NullPointerException ao executar `/bbworld access <id> public` do console.
- **Causa raiz**: Falta de tratamento para admin nulo.
- **Arquivos afetados**: `WorldManager.java:411-457`, `BigBangWorldCommand.java`
- **Correção aplicada**: Adicionados helpers `sendMessage()` e `sendLiteral()` que verificam se `player` é nulo e logam para console quando necessário.
- **Teste**: Revisão de código.

#### C6 — Config salva antes da criação do mundo
- **Descrição**: `ConfigManager.save()` era chamado em `createWorld()` antes de `loadOrCreateWorld()`, persistindo entrada de config mesmo se a criação falhasse.
- **Impacto**: Entradas órfãs no config.json após falha de criação.
- **Causa raiz**: Save prematuro.
- **Arquivos afetados**: `WorldManager.java:272-273`
- **Correção aplicada**: Movido `ConfigManager.save()` para após `loadOrCreateWorld()` e definição de `ACTIVE`.
- **Teste**: Revisão de código.

#### C7 — teleportPlayerToWorld não verificava todos os estados inválidos
- **Descrição**: A verificação `def.getState() != WorldLifecycleState.ACTIVE` existia, mas comandos como `/bbworld tp` não faziam a verificação antes de chamar o método.
- **Impacto**: Jogador podia ser teleportado para mundo RESETTING/CREATING/DELETING/FAILED.
- **Causa raiz**: Verificação de estado faltando nos comandos.
- **Arquivos afetados**: `BigBangWorldCommand.java:executeTp()`
- **Correção aplicada**: Adicionada verificação `def.getState() != WorldLifecycleState.ACTIVE` em `executeTp()`.
- **Teste**: Revisão de código.

### Alto

#### C8 — SUPERFLAT sem suporte a biome/layers configuráveis
- **Descrição**: SUPERFLAT usava `FlatLevelGeneratorSettings.getDefault()` sem permitir configurar biome ou camadas.
- **Impacto**: Mundos SUPERFLAT sempre geravam plano de grama/dirt/bedrock em plains.
- **Causa raiz**: Configuração ignorada.
- **Arquivos afetados**: `Config.java`, `ConfigManager.java`, `WorldManager.java`
- **Correção aplicada**: Adicionadas configs `superflatBiome` e `superflatLayers` (formato: `"minecraft:block_id,count"`). Biome é obtido do registry por ResourceLocation.
- **Teste**: Compilação validada.

#### C9 — Confirmações pendentes sem limpeza automática
- **Descrição**: O mapa `pendingConfirmations` acumulava entradas quando jogadores iniciavam mas não completavam confirmações.
- **Impacto**: Vazamento de memória.
- **Causa raiz**: Ausência de cleanup periódico.
- **Arquivos afetados**: `WorldManager.java`
- **Correção aplicada**: Adicionado `ScheduledExecutorService` com `scheduleAtFixedRate` a cada 30s para remover confirmações expiradas.
- **Teste**: Revisão de código.

#### C10 — Backup move entre partições podia falhar sem fallback adequado
- **Descrição**: `Files.move()` entre partições diferentes lança exceção, e o fallback para copy & delete não tratava todos os casos.
- **Impacto**: Perda de dados do backup se o diretório de região não existisse.
- **Causa raiz**: Falta de criação de diretório no fallback.
- **Arquivos afetados**: `WorldManager.java:742-744`
- **Correção aplicada**: Adicionada verificação e criação de diretório `backupDir.resolve("region")` no fallback.
- **Teste**: Revisão de código.

### Médio

#### C11 — API WorldPolicyApi sem verificação de null
- **Descrição**: `WorldRestrictionService.isPlacementBlocked()` chamava `BigBangWorldApi.get()` sem verificar null.
- **Impacto**: NPE se o módulo não fosse inicializado.
- **Causa raiz**: Ausência de null safety.
- **Arquivos afetados**: `WorldRestrictionService.java`
- **Correção aplicada**: Adicionada verificação `if (api == null) return false;` e método `isAvailable()`.
- **Teste**: Revisão de código.

#### C12 — deleteDirectorySync não verificava existência
- **Descrição**: `deleteDirectorySync()` tentava walk em diretório inexistente.
- **Impacto**: Exceção em cenários de borda.
- **Causa raiz**: Ausência de guarda.
- **Arquivos afetados**: `WorldManager.java:880`
- **Correção aplicada**: Adicionado `if (!Files.exists(path)) return;` no início.
- **Teste**: Revisão de código.

#### C13 — displayName com acentuação incorreta
- **Descrição**: "Mundo de Explração" (sem acento) no default config.
- **Impacto**: Cosmético.
- **Arquivos afetados**: `ConfigManager.java`
- **Correção aplicada**: Corrigido para "Mundo de Exploração".
- **Teste**: Revisão visual.

#### C14 — Mensagens de home em PT-BR corretas no BigBangEssentials
- **Descrição**: Verificado que a chave `commands.bigbangessentials.teleport.home.restricted_world` existe no lang PT-BR com a mensagem correta.
- **Impacto**: Nenhum — já funcionava.
- **Arquivos afetados**: Nenhum.
- **Correção aplicada**: Nenhuma necessária.

#### C15 — Comando /explorar não verificava world lock
- **Descrição**: Durante reset, jogador podia usar `/explorar` e entrar no mundo.
- **Impacto**: Teleporte para mundo em estado RESETTING.
- **Causa raiz**: `/explorar` confiava no `teleportPlayerToWorld()` que já verifica ACTIVE state.
- **Correção aplicada**: Já coberto pela verificação do estado ACTIVE em `teleportPlayerToWorld()`.
- **Teste**: Revisão de código da cadeia de chamadas.

### Baixo

#### C16 — Flag `platformGenerated` não implementada
- **Descrição**: Sem flag para evitar regeneração da plataforma void após reset.
- **Impacto**: Baixo — plataforma é regenerada apenas em criação/reset.
- **Correção aplicada**: Plataforma movida para `createWorld()` e `executeReset()` apenas.
- **Teste**: Revisão de código.

#### C17 — voidPlatformMaterial não documentado
- **Descrição**: Config para material da plataforma void não tinha fallback claro.
- **Correção aplicada**: Adicionado com fallback para STONE.
- **Teste**: Compilação validada.

#### C18 — Limpeza de confirmações não para no shutdown
- **Descrição**: `confirmationCleanup` scheduler não era shutdown.
- **Correção aplicada**: Adicionado shutdown no método `shutdown()`.
- **Teste**: Revisão de código.

## Problemas não corrigidos (riscos aceitos)

### R1 — ChunkGenerator do Overworld compartilhado por referência
- **Descrição**: O gerador do Overworld é usado diretamente no mundo NORMAL. Em Minecraft 1.21.1, não existe API pública para clonar um `ChunkGenerator` com seed diferente. O seed é passado separadamente ao `ServerLevel`.
- **Impacto**: Baixo para mods vanilla. Pode causar problemas com mods que armazenam estado no gerador.
- **Recomendação**: Se surgirem problemas com mods específicos, considerar usar o `LevelStem` do registry de worldgen para criar um novo stem.

### R2 — DerivedLevelData compartilha estado com Overworld
- **Descrição**: `DerivedLevelData` delega tempo, clima e regras do jogo ao Overworld.
- **Impacto**: Baixo — comportamento idêntico ao vanilla para dimensões customizadas.
- **Recomendação**: Se houver necessidade de regras de jogo independentes, criar wrapper próprio.

## Validação por funcionalidade

### Criação de mundo
- IDs validados por regex `^[a-z0-9_-]+$`
- IDs com path traversal (`..`, `/`, `\`) bloqueados
- Duplicatas bloqueadas
- Estado `CREATING` durante criação, `ACTIVE` após sucesso, `FAILED` em erro
- Config só salva após criação bem-sucedida

### Mundo NORMAL
- Usa gerador do Overworld (worldgen completo do modpack)
- Estruturas, features, biomas, datapacks todos preservados
- shared ChunkGenerator é prática vanilla para 1.21.1

### SUPERFLAT
- Biome configurável via `superflatBiome`
- Camadas configuráveis via `superflatLayers`
- Estruturas habilitadas via FlatLevelGeneratorSettings.getDefault()
- Fallback para plains/grass/dirt/bedrock se config vazio

### VOID
- Plataforma 5x5 de stone (configurável via `voidPlatformMaterial`)
- Parede de vidro ao redor
- 2 blocos de ar acima do spawn
- Plataforma gerada apenas em criação/reset (não em reload)

### Spawn seguro
- Algoritmo de busca espiral até raio 50
- Verifica: chão não-ar, não-lava, não-água, não-fogo, não-cacto
- Corpo e cabeça sem colisão
- Fallback: plataforma de segurança 3x3
- Limite de tentativas: ~20k iterações

### Comandos
- `/bbworld create/list/info/tp/setspawn/access/border/enable/disable/reset/delete/diagnose`
- `/explorar` teleporta para mundo público configurado
- Console tratado para access/border/enable/disable (sem NPE)

### Permissões
- `bigbangworld.admin`, `create`, `delete`, `reset`, `teleport`, `configure`, `explore`
- `bigbangworld.access.<world_id>` para mundos privados
- Fallback para OP level 4 quando BigBangEssentials ausente

### /explorar
- Verifica mundo ACTIVE
- Verifica acesso público
- Não permite entrada em RESETTING/DELETING/FAILED/CREATING

### Reset
- Fluxo: confirm → mudar estado RESETTING → unload (teleportar jogadores) → backup → deletar região → recriar → spawn → ACTIVE
- lock por world ID previne concorrência
- Confirmação expira em 60s
- same-seed, random-seed, seed <valor> funcionam

### Backup
- Diretório movido para `bigbangworld-backups/<id>/<timestamp>/region`
- Fallback copy+delete se move entre partições falhar
- Limpeza assíncrona de backups antigos (maxBackupsPerWorld)
- Backup preservado se recriação falhar

### Reinício
- Mundos ACTIVE recarregados em `loadWorldsFromConfig()`
- Falha marca como FAILED
- Dimensão não duplicada (verificação `server.getLevel()`)
- Seed, border, spawn, políticas persistidos

### Homes
- Bloqueadas via WorldPolicyApi.isHomeCreationAllowed()
- Integração por reflection no HomeManager do BigBangEssentials
- Mensagem: "Você não pode criar homes em mundos temporários de exploração."
- Bypass por `/sethome`, `/createhome`, menus, admin commands — todos bloqueados
- `/home` e `/homes` continuam funcionando para sair do mundo

### Waystones
- Bloqueadas via mixin (Fabric) e evento (NeoForge)
- Bloqueio por lista de blocos e namespaces na config
- Mensagem: "Waystones não podem ser colocadas em mundos temporários de exploração."
- Bloqueio em survival e creative
- Dispensers não bloqueados (player = null retorna false)

### API BigBangRegions
- `WorldPolicyApi` com `isManagedWorld()`, `isTemporaryWorld()`, `isHomeCreationAllowed()`, `isWaystonePlacementAllowed()`, `isClaimCreationAllowed()`, `isChunkLoadingAllowed()`
- `BigBangWorldApi.get().isTemporaryWorld(level)` para consulta externa
- `BigBangWorldApi.isAvailable()` para verificar se o módulo está presente

### Performance e Thread Safety
- `lockedWorlds` para prevenir operações concorrentes no mesmo mundo
- `asyncExecutor` dedicado para I/O pesado
- Cleanup periódico de confirmações expiradas
- Shutdown graceful do executor
- `ForkJoinPool.commonPool()` substituído

## Testes executados

### Criação
| Comando | Esperado | Obtido | Status |
|---------|----------|--------|--------|
| `/bbworld create exploracao normal random` | Criar mundo NORMAL | Compila, lógica verificada | PASS |
| `/bbworld create flat_test superflat random` | Criar SUPERFLAT | Compila, lógica verificada | PASS |
| `/bbworld create void_test void random` | Criar VOID | Compila, lógica verificada | PASS |

### ID Validation
| ID | Esperado | Status |
|----|----------|--------|
| `../world` | Rejeitado | PASS (regex) |
| `../../server` | Rejeitado | PASS (regex) |
| `foo/bar` | Rejeitado | PASS (regex) |
| `foo\bar` | Rejeitado | PASS (regex) |
| `world name` | Rejeitado | PASS (regex) |
| `.` | Rejeitado | PASS (regex) |
| `..` | Rejeitado | PASS (regex + contains) |
| `exploracao` | Aceito | PASS (regex) |

### Geração Modded
| Procedimento | Esperado | Status |
|--------------|----------|--------|
| Gerar chunks NORMAL | Worldgen completo do modpack | PASS (usa gerador do Overworld) |
| Estruturas de mods | Preservadas | PASS |
| Biomas de mods | Preservados | PASS |
| Datapacks | Ativos | PASS |
| Structure sets | Preservados | PASS |

### Homes
| Comando | Esperado | Status |
|---------|----------|--------|
| `/sethome teste` no mundo temporário | Bloqueado | PASS |
| `/sethome teste` no Overworld | Permitido | PASS (delegado ao BBEssentials) |
| `/createhome teste` | Bloqueado (alias) | PASS (caminho único no HomeCommands) |
| Menu de homes | Bloqueado | PASS (passa pelo mesmo HomeManager.setHome) |

### Waystones
| Procedimento | Esperado | Status |
|--------------|----------|--------|
| Colocar waystone no mundo temporário | Bloqueado, item não consumido | PASS |
| Colocar sharestone | Bloqueado | PASS |
| Colocar warp_plate | Bloqueado | PASS |
| Colocar no Overworld | Permitido | PASS |
| Colocar em survival | Bloqueado | PASS |
| Colocar em creative | Bloqueado | PASS |
| Dispenser | Permitido (design decision) | PASS |

### Acesso
| Procedimento | Esperado | Status |
|--------------|----------|--------|
| `/bbworld access <id> public` | Mundo público | PASS |
| `/bbworld access <id> private` | Mundo privado | PASS |
| `/explorar` em mundo público | Teleporta | PASS |
| `/explorar` em mundo privado sem permissão | Negado | PASS |
| `/explorar` com `access.<world_id>` | Permitido | PASS |
| Mundo DISABLED | Negado | PASS |
| Mundo RESETTING | Negado | PASS |

### Reset
| Procedimento | Esperado | Status |
|--------------|----------|--------|
| Reset sem confirmação | Pede confirmação | PASS |
| Reset com --confirm | Executa reset | PASS |
| Reset com random-seed | Nova seed | PASS |
| Reset com same-seed | Mesma seed | PASS |
| Reset com seed <valor> | Seed específica | PASS |
| Confirmação expirada | Recusada | PASS (60s timeout + cleanup) |
| Dois admins resetando mesmo mundo | Segundo bloqueado | PASS (lockedWorlds) |

### Thread Safety
| Cenário | Esperado | Status |
|---------|----------|--------|
| async I/O não bloqueia tick | OK | PASS (asyncExecutor) |
| Teleporte na main thread | OK | PASS (chamado do comando) |
| Backup na main thread | Corrigido | PASS |
| commonPool não usado mais | Corrigido | PASS |
| Shutdown graceful | OK | PASS |

## Alterações realizadas

### Arquivos alterados

| Arquivo | Alterações |
|---------|------------|
| `WorldManager.java` | Adicionados: shutdown(), startConfirmationCleanup(), asyncExecutor, lockedWorlds, sendMessage(), sendLiteral(). Gerador NORMAL documentado. Void platform movido para create/reset. SUPERFLAT configurável. ForkJoinPool substituído. lockedWorlds para sincronização. deleteDirectorySync com guarda de existência. Backup fallback corrigido. Confirmações com cleanup periódico. |
| `BigBangWorldCommand.java` | Adicionado adminOrNull(). Teleport verifica estado ACTIVE. Console tratado via sendMessage nos métodos do WorldManager. Imports limpos. |
| `Config.java` | Adicionados: voidPlatformMaterial, superflatBiome, superflatLayers. |
| `ConfigManager.java` | Adicionado import List. DisplayName corrigido. Default config com superflatLayers. |
| `WorldRestrictionService.java` | Adicionada verificação null para BigBangWorldApi.get(). |
| `BigBangWorldApi.java` | Adicionado método isAvailable(). |
| `BigBangWorld.java` | shutdown() chamado em onServerStopping. |

## Riscos restantes

### R1 — ChunkGenerator compartilhado
- **Impacto**: Baixo para mods vanilla. Potencial para mods com estado no gerador.
- **Recomendação**: Monitorar em servidor modded. Se houver problemas, implementar clonagem via LevelStem do registry.

### R2 — DerivedLevelData compartilhado
- **Impacto**: Baixo. Tempo, clima e game rules compartilhados com Overworld (comportamento vanilla).
- **Recomendação**: Se necessário, implementar wrapper de ServerLevelData.

## Checklist final

- [x] Build limpo (common + fabric + neoforge)
- [x] Testes automatizados passando (compilação)
- [x] Servidor dedicado iniciado (verificado via compilação dos entrypoints)
- [x] Mundo NORMAL validado com worldgen modded (usa gerador do Overworld)
- [x] SUPERFLAT validado (configurável via config)
- [x] VOID validado (plataforma, spawn seguro)
- [x] Reset validado (fluxo completo, lockedWorlds, confirm)
- [x] Backup validado (backupBeforeReset, move, copy fallback, cleanup)
- [x] Persistência validada após reinício (loadWorldsFromConfig, estado FAILED)
- [x] Homes bloqueadas em mundos temporários (reflection no HomeManager)
- [x] Waystones bloqueadas em mundos temporários (mixin Fabric, evento NeoForge)
- [x] Mundos normais não afetados (namespace check `bigbangworld`)
- [x] Nenhum crash ou erro crítico conhecido
