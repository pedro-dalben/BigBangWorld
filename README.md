# BigBangWorld

BigBangWorld é um mod server-side de Minecraft que permite a criação, administração, teletransporte e reset de mundos adicionais/temporários em servidores modded.

Documentação técnica da criação de dimensões e integrações de worldgen: [docs/WORLDGEN_INTEGRATION.md](docs/WORLDGEN_INTEGRATION.md).

Este mod foi projetado especificamente para rodar com suporte multi-loader (**Fabric** e **NeoForge**) na versão **1.21.1**.

## Características principais

- **Suporte a Três Tipos de Geração de Mundo**:
  - `NORMAL`: Geração padrão de sobrevivência utilizando a seed configurada.
  - `SUPERFLAT`: Geração superplana usando as configurações padrão do Minecraft.
  - `VOID`: Geração de mundo vazio com uma plataforma central segura de pedra (5x5) cercada por vidros na altura Y=96.
- **Ciclo de Vida Completo dos Mundos**:
  - Transições de estados de mundos controlados por máquina de estados (`CREATING`, `ACTIVE`, `DISABLED`, `RESETTING`, `DELETING`, `FAILED`).
  - Bloqueio de entrada de jogadores em mundos que estejam sofrendo reset ou remoção.
- **Sistema de Backups Inteligente**:
  - Backups automáticos gerados antes de cada reset de mundo (armazenados em `world/bigbangworld-backups/<world_id>/`).
  - Rotação e limpeza automática de backups antigos com limite máximo configurável para evitar sobrecarga de disco.
  - Operações de exclusão e limpeza executadas de forma assíncrona fora da thread principal de tick do servidor.
- **Integração de Políticas com BigBangEssentials**:
  - APIs integradas (`WorldPolicyApi` e `BigBangWorldApi`) para verificação de políticas de mundos gerenciados.
  - Bloqueio automático de criação/modificação de lares (homes) em mundos temporários de exploração.
  - Bloqueio e restrição de posicionamento de blocos de Waystones (Waystone, Sharestone, Portstone, Warp Plate) e claims/proteções de terrenos em mundos temporários.

---

## Comandos Disponíveis

Todos os comandos de administração do BigBangWorld exigem a permissão `bigbangworld.admin` (ou permissão OP de nível 4 por padrão).

### Comandos de Administração (`/bbworld`)

- `/bbworld create <id> <normal|superflat|void> [seed]`: Cria e inicializa um novo mundo dinâmico com o tipo de geração e seed especificados.
- `/bbworld list`: Lista todos os mundos registrados, exibindo seu ID, nome, tipo de geração, estado atual do ciclo de vida, quantidade de jogadores online e tipo de acesso.
- `/bbworld info <id>`: Exibe informações detalhadas sobre as configurações de um mundo específico.
- `/bbworld tp <id> [player]`: Teleporta o administrador (ou o jogador especificado) para o spawn seguro do mundo indicado.
- `/bbworld setspawn <id>`: Define a posição de spawn do mundo para as coordenadas atuais do jogador.
- `/bbworld access <id> <public|private>`: Define o acesso ao mundo (mundos privados exigem a permissão `bigbangworld.access.<world_id>`).
- `/bbworld border <id> <diameter|off>`: Configura ou desativa a borda de mundo (World Border) do mundo especificado.
- `/bbworld enable <id>`: Carrega e ativa o mundo.
- `/bbworld disable <id>`: Desativa o mundo e teleporta todos os jogadores presentes nele de volta ao Overworld.
- `/bbworld reset <id> [same-seed|random-seed|seed <seed>] [--confirm]`: Inicia o processo de reset de mundo.
- `/bbworld delete <id> [--confirm]`: Remove o mundo e apaga permanentemente seus arquivos físicos associados de forma assíncrona.
- `/bbworld diagnose <id>`: Realiza um diagnóstico detalhado da geração do mundo, compatibilidade de estruturas de mods (como Cobblemon e Cobblemon Extra Structures) e políticas ativas.

### Comando de Jogadores (`/explorar`)

- `/explorar`: Teleporta o jogador para o mundo padrão de exploração (configurado na propriedade `defaultExplorationWorld`). Exige a permissão `bigbangworld.explore`.

---

## Configuração (`config/bigbangworld/config.json`)

```json
{
  "defaultExplorationWorld": "exploracao",
  "fallbackDimension": "minecraft:overworld",
  "backupBeforeReset": true,
  "maxBackupsPerWorld": 3,
  "defaultWorldBorderDiameter": 20000.0,
  "restrictedPlacementBlocks": [
    "waystones:waystone",
    "waystones:sharestone",
    "waystones:portstone",
    "waystones:warp_plate"
  ],
  "restrictedPlacementNamespaces": [],
  "worlds": []
}
```

---

## Licença

Desenvolvido por Pedro Dalben. Todos os direitos reservados.
