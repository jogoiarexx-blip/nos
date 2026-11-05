# NÓS — REV5 (a partir de Rádio Mesh REV4)

- Aplicativo renomeado para **NÓS**; imagem fornecida pelo usuário usada como wordmark e ícones Android.
- Identificador `app.getknit.knit.radiomesh` permanece para atualizar a REV4 sem alterar o armazenamento local.
- Novo botão **Apertar e enviar** em grupos: captura voz em memória por até 12 segundos, libera ao soltar, enfileira automaticamente como anexo de voz no grupo criptografado do Knit.
- **Áudio gravado com prévia** original continua na conversa do grupo, intacto; mensagens de texto e chamadas individuais não foram alteradas.
- Especificação inicial do protocolo NÓS PTT v1 (`GroupPttProtocol.kt`) e controle local de turno com limites, TTL, anti-replay, validação de participantes e de origem autenticada. Não há ainda adaptador que distribua esses sinais pelo Mesh nem streaming ao vivo entre membros.
- A tela informa explicitamente **não ao vivo**; os áudios podem chegar após reconexão (store-and-forward). Não confundir áudio enfileirado com entregue.
- Nenhum servidor novo, migração de banco ou mudança no protocolo legado.

## Limitações para REV6

1. Integrar sinais de piso/turno autenticados ao transporte de grupo do Knit com adesão obrigatória de todos os participantes; testar concorrência, perda de mensagens e partições de rede.
2. Integrar codec Opus em pequenos pacotes de streaming efêmeros com retransmissão Mesh de baixa latência e controle de congestionamento; não reutilizar mensagens custodiadas para stream.
3. Negociar capacidade `CAP_GROUP_PTT` com pares compatíveis, implementar escuta e relé, proteção contra replay e perda de pacotes.
4. Testar 3+ celulares Android fisicamente offline. Evitar prometer alcance, entrega ou latência.

## Verificação

Usar `kotlinc` para testes isolados do codec e do controle de turnos (`scripts/nos_rev5_core_test.kt`). A compilação completa depende da distribuição Gradle e Android SDK.
