# Worldgen e integrações automáticas

Este documento descreve como o BigBangWorld cria dimensões, prepara o worldgen dos mods e integra dimensões ativas ao Cobblemon Raid Dens.

## Objetivo

Uma dimensão `bigbangworld:<id>` do tipo `NORMAL` deve usar o worldgen completo do Overworld disponível no servidor:

- biomas e fonte de biomas do Overworld;
- noise settings do Overworld;
- `structure` e `structure_set` carregados pelos mods e datapacks;
- regras de spawn e progressão dos mods que suportam dimensões customizadas.

O BigBangWorld não coloca estruturas manualmente em coordenadas, não altera os arquivos de configuração dos mods e não regenera chunks já explorados.

## Fluxo de criação

`/bbworld create` não cria a dimensão imediatamente. Ele grava uma operação pendente e exige reinício:

```text
/bbworld create teste normal random
```

No próximo carregamento do servidor, o mixin `MinecraftServerBootstrapMixin` chama `WorldBootstrap` no início de `loadLevel`, antes da geração de chunks.

O bootstrap executa, nesta ordem:

1. carrega `config/bigbangworld/config.json` e as operações pendentes;
2. aplica criação, reset ou exclusão pendente;
3. recupera estados órfãos;
4. gera/atualiza `world/datapacks/bigbangworld-dimensions`;
5. aplica as integrações em memória;
6. permite que o Minecraft carregue os datapacks e as dimensões.

Depois do reinício, a dimensão pode ser acessada com:

```text
/bbworld tp teste
```

## Gerador NORMAL

O datapack interno cria uma definição equivalente ao gerador vanilla de noise do Overworld:

```json
{
  "type": "minecraft:overworld",
  "generator": {
    "seed": 12345,
    "type": "minecraft:noise",
    "settings": "minecraft:overworld",
    "biome_source": {
      "type": "minecraft:multi_noise",
      "preset": "minecraft:overworld"
    }
  }
}
```

Os `structure_sets` não são listados manualmente no JSON da dimensão. O Minecraft monta o estado de estruturas usando os registries carregados para o mundo; por isso estruturas adicionadas por Repurposed Structures, Legendary Monuments e datapacks compatíveis participam da geração normal.

`SUPERFLAT` e `VOID` continuam usando geradores próprios e não recebem a promessa de estruturas naturais do Overworld.

## Atualização do datapack

Em todo bootstrap, o gerador:

- cria `world/datapacks/bigbangworld-dimensions` se necessário;
- remove somente os JSONs de dimensão gerados anteriormente;
- escreve novamente uma dimensão para cada mundo `ACTIVE`;
- reescreve `pack.mcmeta`.

Isso evita dimensões removidas ou desativadas ficarem acumuladas no datapack. Criação, reset e exclusão são refletidos no próximo bootstrap porque as operações são processadas antes da geração do datapack.

## Cobblemon Raid Dens

A integração é opcional e compile-safe. O BigBangWorld não possui dependência de compilação do Raid Dens.

Quando `cobblemonraiddens` está carregado, o gateway acessa em memória o objeto de configuração do mod e, para cada dimensão ativa que ainda não possui entrada, copia os valores atuais do Overworld:

- `dimension_tier_weights`: copia os sete pesos do Overworld;
- `dimension_spawn_rate`: copia a taxa do Overworld.

Com a configuração do servidor atual, uma dimensão nova recebe inicialmente:

```text
dimension_tier_weights = [10, 16, 20, 24, 22, 6, 2]
dimension_spawn_rate = 480
```

Entradas já configuradas são preservadas. O arquivo original `config/cobblemonraiddens/common.json5` nunca é editado e nenhuma entrada é acumulada no disco. A operação é repetida em cada inicialização.

Sem o mod instalado, a integração é ignorada e o BigBangWorld continua funcionando.

## Repurposed Structures e Legendary Monuments

Esses mods não precisam de uma configuração especial do BigBangWorld para o worldgen NORMAL. Se estiverem instalados antes da criação/reset da dimensão, seus registries, biomas, estruturas e datapacks disponíveis no carregamento do servidor participam da geração.

As limitações próprias dos mods continuam valendo: biome restrictions, progressão, condições de spawn e chunks já existentes não são alterados pelo gateway.

## Radical Cobblemon Trainers (RCT)

RCT é uma integração diferente: seus treinadores são entidades de spawn natural, não `structure_sets` do BigBangWorld.

O arquivo `rctmod-server.toml` atual possui `dimensionWhitelist = []` e não inclui `bigbangworld:*` na blacklist, portanto não há bloqueio configurado para essas dimensões. O BigBangWorld não altera o progresso do Trainer Card.

O próprio RCT pode não apontar corretamente para um treinador que esteja em outra dimensão. Para garantir a experiência guiada pelo mapa/Trainer Card, o jogador deve procurar o treinador na dimensão indicada pelo mod, normalmente o Overworld. Caso RCT deva ficar restrito ao Overworld, configure as dimensões BigBangWorld em `dimensionBlacklist` e reinicie.

## Reset e chunks existentes

Trocar o datapack não recria chunks já explorados. Para testar estruturas com segurança:

```text
/bbworld reset teste random-seed
/bbworld reset teste --confirm
```

O reset é concluído no próximo reinício. Com backup habilitado, a dimensão antiga vai para `world/bigbangworld-backups/<id>/` antes da recriação.

## Diagnóstico

Use:

```text
/bbworld diagnose <id>
```

O diagnóstico informa:

- tipo e estado da dimensão;
- se o tipo é `NORMAL`;
- classe do gerador e fonte de biomas quando carregada;
- quantidade de `structure` e `structure_set` nos registries;
- status do Cobblemon Raid Dens;
- presença de Repurposed Structures;
- presença de Legendary Monuments.

No log de inicialização, uma integração bem-sucedida registra:

```text
[BigBangWorld] CobblemonRaidDens enabled for X managed dimensions
```

## Procedimento de homologação

1. Instale o jar correto para o loader usado e reinicie o servidor.
2. Confirme que os mods de worldgen estão instalados antes de criar o mundo.
3. Crie uma dimensão `NORMAL` e reinicie novamente.
4. Rode `/bbworld diagnose <id>`.
5. Teleporte para a dimensão e gere chunks novos.
6. Use `/locate structure` para estruturas que o mod disponibiliza nesse comando.
7. Aguarde a geração natural de Raid Dens ou use as ferramentas de diagnóstico do próprio mod.
8. Reinicie o servidor e confirme que o status permanece ativo.
9. Faça um reset completo e repita o teste.
10. Repita sem um dos mods opcionais e confirme que não há crash.

## Falhas comuns

| Sintoma | Causa provável | Ação |
|---|---|---|
| Estrutura não aparece em área antiga | Chunk já foi explorado | Criar chunks novos ou resetar a dimensão |
| Raid Dens aparece como desabilitado | Mod ausente, config não inicializada ou erro de integração | Conferir jar, log e `/bbworld diagnose` |
| Trainer Card gira sem apontar | Treinador está em outra dimensão | Ir para a dimensão do treinador; isso é comportamento do RCT |
| Limite de terreno em uma coordenada fixa | World border, claim ou proteção externa | Verificar `/worldborder`, claims e logs do plugin/mod responsável |
| Mundo não cria após `/bbworld create` | Operação ainda pendente | Reiniciar o servidor e conferir `config/bigbangworld/config.json` |

