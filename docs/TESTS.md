# Plano de Testes e Evidência de Compilação

Este documento descreve os casos de teste planejados para homologação do mod **BigBangWorld** e apresenta a evidência de sucesso na compilação do projeto.

---

## 1. Casos de Teste Estruturados

| ID | Cenário de Teste | Ações Realizadas | Resultado Esperado | Status |
|---|---|---|---|---|
| **CT-01** | Inicialização do Mod | Iniciar o servidor com o mod instalado pela primeira vez. | Arquivo `config/bigbangworld/config.json` gerado automaticamente com definições padrão e mundo `exploracao` cadastrado. Banner exibido no log. | **Aprovado** |
| **CT-02** | Criação de Mundo (`NORMAL`) | Executar `/bbworld create teste_normal normal 12345` | Mundo criado com sucesso, plataforma/local de spawn seguro encontrado, criador teleportado ao spawn do mundo. Mensagem de sucesso em verde. | **Aprovado** |
| **CT-03** | Criação de Mundo (`VOID`) | Executar `/bbworld create teste_void void 54321` | Plataforma de pedra de 5x5 com borda de vidro gerada em Y=96. Criador teleportado acima dela. | **Aprovado** |
| **CT-04** | Teletransporte de Jogadores | Jogador executa `/explorar` | Se o mundo `exploracao` (padrão) não estiver criado/ativo, exibe erro em vermelho. Se ativo, teleporta o jogador com segurança. | **Aprovado** |
| **CT-05** | Restrição de Homes | Estar em mundo temporário e executar `/sethome casa` no BigBangEssentials | O mod BigBangEssentials cancela a ação via reflexão e retorna a mensagem: *Você não pode criar homes em mundos temporários de exploração.* | **Aprovado** |
| **CT-06** | Restrição de Waystones | Estar em mundo temporário e colocar uma Waystone no chão | A colocação do bloco é bloqueada na origem. O item é mantido no inventário e exibe mensagem em vermelho. | **Aprovado** |
| **CT-07** | Desabilitação de Mundo | Executar `/bbworld disable teste_normal` | Todos os jogadores no mundo `teste_normal` são evacuados para o spawn do Overworld. O mundo é descarregado e removido do tick do servidor. | **Aprovado** |
| **CT-08** | Reset de Mundo (Com confirmação) | Executar `/bbworld reset teste_normal` e depois `/bbworld reset teste_normal --confirm` | Jogadores evacuados. O mundo é descarregado, a pasta é renomeada para backup instantaneamente, uma nova dimensão é gerada com o estado `ACTIVE`, spawn e plataformas gerados novamente. | **Aprovado** |
| **CT-09** | Exclusão de Mundo (Assíncrona) | Executar `/bbworld delete teste_normal` e `/bbworld delete teste_normal --confirm` | Mundo descarregado, removido das configurações do mod, e os arquivos em disco apagados em segundo plano sem travar a thread de tick. | **Aprovado** |
| **CT-10** | Diagnóstico de Mod | Executar `/bbworld diagnose exploracao` | Exibe resumo técnico do mundo, gerador utilizado, quantidade de estruturas registradas nos registries dinâmicos e status de integração (Cobblemon, Waystones). | **Aprovado** |
| **CT-11** | Worldgen de Mods | Criar/resetar mundo `NORMAL` com Repurposed Structures e Legendary Monuments instalados; gerar chunks novos | O diagnóstico mostra worldgen NORMAL e `structure_sets` carregados; estruturas compatíveis podem ser localizadas nos chunks novos. | **Pendente em servidor modpack** |
| **CT-12** | Integração Raid Dens | Iniciar com Cobblemon Raid Dens e uma dimensão `NORMAL` ativa | Log registra a integração e `/bbworld diagnose <id>` mostra Raid Dens habilitado; o arquivo `common.json5` original permanece inalterado. | **Pendente em servidor modpack** |
| **CT-13** | Compatibilidade Opcional | Iniciar sem Repurposed Structures, Legendary Monuments ou Cobblemon Raid Dens | BigBangWorld inicia sem crash e o diagnóstico marca o mod ausente. | **Pendente em servidor modpack** |

---

## 2. Evidência de Sucesso na Compilação

O projeto foi construído usando a infraestrutura Gradle multi-loader. Apresentamos abaixo o log final da compilação bem-sucedida:

```
> Configure project :
==========================================
Building BigBangWorld v1.0.0.0+build.4
Build #4
==========================================

> Configure project :fabric
Fabric Loom: 1.9.2

...
> Task :common:compileJava
> Task :common:classes
> Task :common:jar
> Task :neoforge:compileJava
> Task :neoforge:classes
> Task :neoforge:jar
> Task :neoforge:assemble
> Task :neoforge:build
> Task :fabric:compileJava
> Task :fabric:classes
> Task :fabric:jar
> Task :fabric:remapJar
> Task :fabric:assemble
> Task :fabric:build

BUILD SUCCESSFUL in 3s
17 actionable tasks: 12 executed, 5 up-to-date
```
