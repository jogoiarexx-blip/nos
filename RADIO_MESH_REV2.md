# Rádio Mesh — REV2 | chamada direta 1:1

**Base:** Knit 2.10.2, preservando integralmente o motor de chamadas, o transporte e o protocolo original.
**Tipo de entrega:** código-fonte Android; APK não compilado neste ambiente.

## Melhorias desta revisão

1. **Chamar no rádio** na Central de Rádio para cada conversa privada recente; **Mensagens** permanece separado.
2. **Escolher contato** agora abre um seletor de destinatário de rádio, no qual somente uma pessoa pode ser selecionada. Confirmar tenta iniciar uma chamada WALKIE diretamente, sem abrir o chat antes.
3. **RadioDialViewModel** encaminha solicitações reais ao `CallManager.start(peerId, CallKind.WALKIE)`. Não cria um canal de rádio fictício nem recorre automaticamente a chamada LIVE.
4. Checagens locais antes de aceitar/discar: ID não vazio, não é o próprio aparelho, não está bloqueado, build tem walkie, perfil fixado disponível e `CAP_WALKIE` anunciado.
5. Feedback de erro para contato bloqueado, sem suporte, função indisponível, chamada ocupada e falhas de inicialização; desabilita novos botões enquanto processa uma solicitação.
6. Recursos de texto em português brasileiro e inglês, sem modificar a tradução original fora da Central.
7. Testes adicionados: política de validação, botão do Hub, filtragem da lista e seleção unitária de contato (testes novos **escritos, ainda não executados**).

## Restrições reais

- Walkie Mesh continua **individual**, com botão PTT na tela de chamada do Knit. Grupo de mensagens e sala Nearby **não** viraram canais públicos de voz.
- Checar `CAP_WALKIE` evita chamadas incompatíveis conhecidas, mas não garante que o contato esteja acessível agora. O estabelecimento depende de conectividade local, topologia e permissões do aparelho.
- Não foram realizadas medições de alcance, áudio/latência ou duração da bateria em telefones físicos.
- O GitHub Actions do projeto tem `:app:assembleDebug` e testes JVM para produzir APK debug e relatórios quando o código for publicado; **nenhum workflow foi executado neste ambiente**.
- A compilação local foi bloqueada por indisponibilidade do Gradle 9.7.1 e falta de acesso a services.gradle.org; portanto **não é possível atestar build verde ou APK funcional**.

## Como verificar

- `./gradlew :app:testDebugUnitTest --tests 'app.getknit.knit.ui.radiohub.*'`
- `./gradlew :app:assembleDebug`
- Instalar o APK debug em **dois aparelhos Android**, habilitar Bluetooth/Wi-Fi e microfone.
- Adicionar um contato em ambos; abrir **Rádio offline > Escolher contato**, selecionar uma única pessoa e tocar na confirmação.
- Repetir pela lista **Conversas recentes > Chamar no rádio**.
- Validar que o destino recebe a chamada, que o botão PTT transmite voz, e que a chamada termina corretamente.
- Bloquear um contato e repetir: deve apresentar bloqueio e **não** iniciar chamada.
- Testar aparelhos com build antigo sem CAP_WALKIE: deve apresentar incompatibilidade.
- Verificar também mensagens Nearby e chats de grupo; devem continuar sendo somente texto/arquivos, sem voz em grupo.

## Próximo alvo técnico

Um canal PTT de grupo requer projeto específico de endereçamento, autorização, latência, justiça de transmissão/turnos, múltiplos receptores, congestionamento e controle da criptografia. Não deve ser implementado duplicando uma chamada 1:1 para cada integrante.

## Licença

Fork de https://github.com/getknit/knit, código sob GPL-3.0-or-later. Preservar atribuição e disponibilizar o código correspondente na distribuição do APK.

**Instalação sobre a REV1:** `knit.versionCode=38` (REV1 tinha 37) para permitir atualização do APK ao ser compilado e assinado pela mesma chave. Se a assinatura for diferente, Android exigirá desinstalar a versão anterior, com risco de perda de dados locais sem backup. O `versionName=2.10.2` permanece igual ao da base; a revisão do fork é identificada por este documento.
