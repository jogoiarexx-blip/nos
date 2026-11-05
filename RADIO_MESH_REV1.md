# Rádio Mesh — Revisão 1 (fork experimental do Knit 2.10.2)

## Mudanças realmente implementadas

- Nova **Central de Rádio** (`RadioHubScreen.kt`), acessível pelo ícone de microfone
  na barra superior das conversas ou pelo menu de opções.
- Exibição do número de aparelhos diretamente conectados, estado da rede Mesh,
  e ações reais para religar ou retomar a rede previamente pausada.
- Seleção de contatos para iniciar uma conversa privada e usar a chamada Mesh
  **walkie-talkie já existente** no Knit (se ambos os aparelhos forem compatíveis).
- Acesso explícito à sala pública **Nearby**, identificada corretamente como sala
  de **mensagens**, não de voz ao vivo.
- Lista de conversas privadas recentes, sem apresentar conversas em grupo ou
  salas públicas como se fossem destinatários de chamada individual.
- Textos da Central de Rádio em português brasileiro e inglês. Outros textos
  do Knit continuam em inglês quando ainda não foram traduzidos.
- Nome visível **Rádio Mesh** e `applicationId` próprio
  `app.getknit.knit.radiomesh`, evitando substituir o Knit original instalado.
- Dois testes unitários de filtragem de conversas na Central de Rádio.

## Importante: o que NÃO foi implementado nesta revisão

- Não existe ainda canal de voz público (PTT de 1 para muitos).
- Não foi criado protocolo de voz ou retransmissão novo.
- Não houve alteração no código criptográfico, nas chaves, no formato de pacotes,
  nos rádios BLE/Wi-Fi Aware ou na lógica de entrega Mesh.
- Não foi testado áudio ou alcance entre dois aparelhos reais.
- O APK não foi compilado nesta entrega; exige Android SDK (compile SDK 37.1),
  JDK 21 e dependências Gradle do projeto.

## Como testar em Android

1. Instale as ferramentas exigidas no README original do Knit e o Android SDK indicado.
2. Execute `./gradlew :app:testDebugUnitTest` e `./gradlew :app:assembleDebug`.
   Ao publicar no GitHub, o CI existente também passa a oferecer o artefato
   **radio-mesh-debug-apk** (instalação manual, não é uma versão assinada de produção).
3. Instale o APK de depuração no **Celular A** e em outro **Celular B**.
4. Complete as permissões BLE/Wi-Fi/microfone e a configuração inicial.
5. Aproxime os aparelhos; confirme que a Central de Rádio mostra o status.
6. Adicione um contato, abra o chat, toque em chamada Mesh e teste apertar para falar.
7. Envie mensagens sem conexão móvel nem internet; depois teste entrega via terceiro aparelho.
8. Teste pausas e retomadas; confirme que o Knit original, quando instalado, permanece intacto.

Para o microfone funcionar, a permissão de áudio do Android precisa estar concedida.
O próprio Knit decide se a chamada Mesh está disponível e exibe o botão quando compatível.

## Base e licença

Base: https://github.com/getknit/knit — Knit 2.10.2.
Copyright dos respectivos autores mantido; licença **GPL-3.0-or-later** em `COPYING`.
Distribuições desse fork devem continuar respeitando a GPL e disponibilizar o
código-fonte correspondente. Não atribuir os novos recursos aos autores originais.

Este projeto não tem associação oficial com os mantenedores do Knit.
