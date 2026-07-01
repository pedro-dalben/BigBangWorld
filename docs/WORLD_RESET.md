# Fluxo de Reset e Gerenciamento de Ciclo de Vida dos Mundos

Este documento detalha os aspectos técnicos da máquina de estados, segurança de jogadores e do sistema de backups utilizados pelo BigBangWorld durante o ciclo de vida e reset dos mundos.

---

## Máquina de Estados do Ciclo de Vida

Cada mundo dinâmico gerenciado pelo mod possui um estado definido no enum `WorldLifecycleState`. As transições ocorrem de acordo com o seguinte diagrama:

```
[Disabled/Failed] ──> CREATING ──> ACTIVE ──> DISABLED
                     │           ▲  │
                     ▼           │  ▼
                    FAILED <─────┴─ RESETTING ──> DELETING
```

- **`CREATING`**: O mundo está sendo inicializado e gerado pela primeira vez. Jogadores não podem entrar.
- **`ACTIVE`**: O mundo está carregado e disponível para exploração e teletransporte.
- **`DISABLED`**: O mundo foi descarregado da memória. Ele ainda existe em disco, mas não aceita ticks ou jogadores.
- **`RESETTING`**: O mundo está passando pelo processo de evacuação de jogadores, salvamento, descarregamento da memória e renomeação física da pasta.
- **`DELETING`**: O mundo está sendo excluído permanentemente da lista de configurações e seus arquivos físicos estão sendo apagados de forma assíncrona.
- **`FAILED`**: Indica que ocorreu uma falha grave na geração, carregamento ou reset. O mundo fica indisponível para jogadores até que um administrador resolva o problema.

---

## O Processo de Reset Passo a Passo

Quando um administrador executa `/bbworld reset <world_id>`:

1. **Confirmação Prévia**:
   - É enviado um alerta ao administrador detalhando os riscos de perda de dados.
   - O comando exige confirmação rápida em até 60 segundos com o parâmetro `--confirm` para evitar resets acidentais.

2. **Mudança de Estado**:
   - O estado do mundo é alterado para `RESETTING` no arquivo `config.json`.
   - A partir desse momento, qualquer tentativa de jogador de se teletransportar para o mundo via `/explorar` ou `/bbworld tp` é bloqueada.

3. **Evacuação Segura de Jogadores**:
   - O mod identifica todos os jogadores atualmente presentes na dimensão sendo resetada.
   - Todos são teleportados para a dimensão de fallback configurada (padrão: `minecraft:overworld`) no seu spawn global, garantindo que nenhum jogador caia no vácuo ou fique preso durante o descarregamento.

4. **Fechamento e Liberação de Recursos**:
   - O nível é salvo no disco utilizando o método `save(null, true, false)` para descarregar todos os blocos e entidades.
   - Em seguida, o método `close()` da `ServerLevel` é chamado para liberar os manipuladores de arquivos (file handles) e trancas físicas (locks) dos arquivos de região da pasta da dimensão.
   - Por fim, a dimensão é removida do mapa de mundos ativos `levels` do `MinecraftServer`.

5. **Renomeação Instantânea de Pasta (Backup)**:
   - Se os backups estiverem habilitados, o mod move a pasta da dimensão (localizada em `world/dimensions/bigbangworld/<world_id>`) para `world/bigbangworld-backups/<world_id>/<timestamp>/` usando renomeação de arquivos nativa do sistema operacional (`Files.move`).
   - Mover a pasta por renomeação é uma operação de metadados instantânea que não gera travamentos ou lag de tick.
   - Se a movimentação falhar (ex: através de partições diferentes), o mod faz uma cópia recursiva e deleta os arquivos antigos.

6. **Limpeza Assíncrona de Backups Antigos**:
   - A remoção de backups antigos que excedam o limite configurado (`maxBackupsPerWorld`) é despachada para o pool de threads assíncronas do Java (`ForkJoinPool.commonPool()`).
   - Isso garante que a thread principal de ticks do Minecraft continue livre para processar o jogo enquanto os arquivos de backups antigos são limpos em segundo plano.

7. **Geração e Inicialização do Novo Mundo**:
   - Um novo `ServerLevel` é instanciado usando a mesma configuração de biomas e gerador de chunks, mas utilizando a seed escolhida.
   - Se for um mundo `VOID`, uma nova plataforma central de pedra de 5x5 com bordas de vidro é gerada no nível Y=96.
   - Se for `NORMAL` ou `SUPERFLAT`, o mod executa uma pesquisa em espiral por uma área de spawn que possua solo firme e espaço livre de sufocamento. Caso não encontre, gera uma plataforma de emergência de 3x3 blocos de pedra.
   - O novo spawn é registrado na dimensão e o mundo volta ao estado `ACTIVE`.
