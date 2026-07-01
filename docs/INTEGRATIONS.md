# Integrações do Mod e Políticas de Restrição

Este mod oferece integrações seguras e dinâmicas com outros componentes do ecossistema do servidor, garantindo consistência e impedindo que jogadores estabeleçam bases ou pontos de teletransporte permanentes em mundos temporários de exploração.

---

## 1. Integração com BigBangEssentials (Lares/Homes)

O BigBangWorld interage de forma compile-safe (segura contra falhas de carregamento caso o outro mod não esteja presente) com o **BigBangEssentials**.

### Funcionamento do Vínculo
Durante a criação de um lar via `/sethome` no BigBangEssentials, o método `HomeManager#setHome` intercepta a operação e executa uma chamada dinâmica via reflexão à classe `BigBangWorldApi`:

```java
Class<?> apiClass = Class.forName("com.pedrodalben.bigbangworld.api.BigBangWorldApi");
Object apiInstance = apiClass.getMethod("get").invoke(null);
```

Se o BigBangWorld estiver ativo e a dimensão atual for identificada como temporária (`isTemporaryWorld`), a API consulta as políticas configuradas do mundo:

- Se a política `allowHomeCreation` for `false`, o BigBangEssentials cancela imediatamente a criação/modificação da home e retorna a mensagem localizada:
  `§cVocê não pode criar homes em mundos temporários de exploração.`

---

## 2. Bloqueio de Waystones e Blocos de Teleporte

Para evitar a colocação de pontos de teletransporte indestrutíveis ou permanentes de outros mods (ex: Waystones, Sharestone, Portstone, Warp Plates), o BigBangWorld possui interceptores de eventos de colocação de blocos específicos para cada loader.

### Métodos de Interceptação
- **Fabric**: Utiliza o Mixin `BlockItemMixin` injetado na cabeça do método `place` da classe `BlockItem`:
  ```java
  @Inject(method = "place", at = @At("HEAD"), cancellable = true)
  ```
- **NeoForge**: Assina o evento `BlockEvent.EntityPlaceEvent` no barramento comum de eventos do NeoForge:
  ```java
  @SubscribeEvent
  public static void onBlockPlace(BlockEvent.EntityPlaceEvent event)
  ```

### Verificação de Restrições
A colocação é analisada pelo serviço `WorldRestrictionService`:
1. Verifica se a dimensão atual é administrada e temporária (`isTemporaryWorld`).
2. Verifica se a política `allowWaystones` é falsa.
3. Compara o ID do bloco colocado e o namespace do mod contra as listas `restrictedPlacementBlocks` e `restrictedPlacementNamespaces` do arquivo de configuração `config.json`.
4. Se houver correspondência, o evento é cancelado e uma mensagem de bloqueio é enviada ao jogador:
   `§cWaystones não podem ser colocadas em mundos temporários de exploração.`

---

## 3. Bloqueio de Claims/Terrenos e Carregamento de Chunks

Através da implementação de `WorldPolicyApi`, o mod expõe endpoints para que outros mods e plugins consultem regras específicas do mundo antes de conceder permissões:

- **isClaimCreationAllowed(ServerPlayer player)**: Consultas a mods de claim de terrenos (como FTB Chunks ou Claims do BigBang) utilizam este método para verificar se o jogador tem permissão para proteger terrenos no mundo atual.
- **isChunkLoadingAllowed(ServerPlayer player)**: Impede que carregadores de chunk (chunk loaders) sejam colocados ou ativados em mundos temporários, otimizando o desempenho do servidor.
