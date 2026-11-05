# Rádio Mesh REV4 — grupos de voz gravada via Mesh

## Resultado desta revisão

- **Canais de rádio em grupo** aparecem na Central de Rádio a partir de *grupos de conversa já existentes*. Não há novo servidor, nova chave, nova mensagem de rede ou protocolo de chamada.
- O botão **Abrir microfone do grupo** abre a conversa do grupo. O compositor existente grava áudio ao segurar, libera uma prévia quando solta e exige toque em **Enviar**; só então inicia o fluxo normal de mensagem de voz cifrada e armazenada para posterior entrega.
- **Criar grupo de voz** abre o seletor dedicado, que exige **pelo menos duas outras pessoas**, evitando abrir um DM como se fosse um canal.
- Grupos com mensagens não lidas aparecem primeiro. Salas públicas de texto (Nearby, commons, Meshtastic) e conversas individuais são excluídas dessa lista.
- A chamada walkie-talkie **1:1** da REV3 permanece intocada; a tela diferencia explicitamente voz gravada de chamada ao vivo. Canais não iniciam chamadas de voz automáticas.
- Traduções novas em pt-BR e inglês, com tags de teste Compose.

## Limitações

- **Não há áudio ao vivo de grupo**, controle de turno global nem retransmissão de streaming. Cada áudio é uma mensagem armazenada e encaminhada quando houver conexão, com latência variável. O microfone fica na tela da conversa do grupo, não na Central.
- O áudio gravado é revisado e enviado manualmente, evitando transmissão acidental.
- A existência de um grupo não garante a entrega a todos os participantes; rotas e compatibilidade variam.
- Ainda faltam execução de `assembleDebug` e testes em 3 Androids sem rede de dados. Não anunciar APK funcional antes de validar esses testes.
- O projeto base permanece GPL-3.0-or-later e preserva os avisos de atribuição.

## Roteiro de teste

1. Abrir Central de Rádio, criar grupo com dois contatos, enviar nota de voz da conversa e repetir offline com 3 celulares.
2. Confirmar áudio recebido por cada participante, atraso após desconexão, retomada de entrega e no mínimo 3 tentativas por cenário.
3. Garantir que o seletor com 1 contato não cria nem inicia DM e que salas públicas não são mostradas como canais.
4. Verificar chamadas individuais REV3, envio de texto e arquivos de grupo, leitor de tela e permissões.

## Verificações realizadas nesta entrega

- Compilação isolada da regra de listagem/ordenação de grupos e da regra do seletor com `kotlinc` 1.9/JDK 21: **6 asserções passaram**. Não testa o Compose, o transporte nem a criptografia.
- `python scripts/check_radio_rev4.py`: **passou** (55 recursos de texto de rádio presentes em inglês e pt-BR; navegação e ações básicas).
- `patch -p1 --dry-run` contra a REV3: **passou**.
- Tentativa de executar testes Gradle: **bloqueada**, pois `services.gradle.org` não pôde ser resolvido neste ambiente; portanto não há afirmação de APK compilado.
- Testes Robolectric/Compose incluídos, mas **não executados**: filtro, abertura do grupo sem chamada e bloqueio de DM no seletor de grupos.

## Para compilar no PC/CI

`./gradlew :app:assembleDebug :app:testDebugUnitTest`
