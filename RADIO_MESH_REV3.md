# Rádio Mesh REV3 — PTT real, sem prometer voz em grupo

## O que foi implementado

- Estados visuais da chamada **derivados** de `WalkieTalk.talk` e `WalkieTalk.link`, sem simulação:
  `READY`, `RECORDING`, `RECEIVING`, `SENDING`, `NO_ROUTE` e erros.
- A tela indica quando **o contato conectado está falando**, quando o aparelho está
  gravando/transmitindo e quando os pacotes estão sendo enviados.
- Um novo toque não inicia gravação se ainda não há rota, o outro aparelho está
  falando ou o áudio anterior ainda está sendo enviado.
- O gesto PTT usa `pointerInput(Unit)` e `rememberUpdatedState` para não perder
  a pressão longa quando o estado muda de pronto para gravando.
- Texto do rádio localizado em pt-BR, inclusive erros de microfone, estado da rota
  e acessibilidade (ações de iniciar/finalizar transmissão).
- Alertas diferenciam **rota direta**, **mesh por saltos** e **relay pela Internet**.
  Um relay via Internet não deve ser apresentado como comunicação totalmente offline.
- Testes unitários para o controle de PTT e seus estados.

## Limites verificados no código

- Ainda é uma chamada walkie **1:1** de Knit; **não** transmite voz para uma sala.
- `SENT` significa que o áudio foi entregue ao transporte local para transmissão;
  não é confirmação de que o destinatário ouviu.
- O máximo de uma rajada é definido em `WalkiePolicy.MAX_BURST_MS` (60 s).
- A REV3 **não** altera `TalkWire`, chaves criptográficas, formatos de frame,
  retransmissão, nem política de armazenamento.

## REV4 — arquitetura proposta, não implementada

Para canais de voz com várias pessoas, não basta reutilizar `CallManager.start(peerId)`:
precisamos de participantes autenticados, convite/ingresso, controle de turno (PTT),
criptografia e rekey em mudanças de membros, deduplicação por `roomId+burstId+chunkId`,
TTL, limites de banda por rádio e política de replay/retentativa. Mantenha o canal
fechado por padrão e documente alcance/latência sob BLE e Wi-Fi Aware. Só anunciar
voz de grupo após testes em 3+ aparelhos reais, com verificações de perdas e permissão.

## Testes sugeridos em aparelhos físicos

1. Dois Androids compatíveis, sem dados móveis e sem Wi-Fi conectado à Internet,
   permitir Bluetooth/Wi-Fi Aware e microfone.
2. Iniciar chamada pelo botão da central; testar pressionar, segurar por 2 s, soltar,
   cancelar deslizando/retirando o foco, e repetir rapidamente.
3. Testar dois aparelhos tentando transmitir, saída de alcance e reconexão.
4. Checar TalkBack, rotação, headset e Android suspendendo o app.
5. Confirmar que rota `SPOOL` usa Internet e que NÃO é anunciada como offline.

## Compilação

Gradle wrapper: `./gradlew :app:assembleDebug` e
`./gradlew :app:testDebugUnitTest --tests app.getknit.knit.ui.call.RadioPttStateTest`
(requer JDK, Android SDK, Gradle e dependências disponíveis).
