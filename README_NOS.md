# NÓS — Mensageiro e rádio Mesh (REV5)

Fork do **Knit 2.10.2**, com identidade visual NÓS e código-fonte disponível sob **GPL-3.0-or-later**. O projeto original e os avisos de copyright seguem preservados.

## O que já faz

- Conversas criptografadas, grupos, notas de voz com prévia, mensagens offline e walkie-talkie 1:1: implementações provenientes do Knit, com as melhorias das REV1–REV4.
- **PTT de grupo em rajadas**: entrar na Central de Rádio → selecionar um grupo → **Apertar e enviar**. Segurar para gravar até 12 segundos, soltar para colocar uma nota de voz na fila criptografada do grupo. O arquivo é gravado em memória e reutiliza o transporte existente do Knit. Os outros membros podem receber mais tarde; a fila não garante entrega imediata.
- **Modo gravado com prévia**: no mesmo grupo, tocar **Áudio gravado com prévia** para abrir a conversa original, ouvir a nota antes de tocar em Enviar.
- Nova logo fornecida pelo usuário na Central, na tela inicial do celular e nos ícones adaptativos do Android. Nome exibido: **NÓS**.

## O que a REV5 não faz

**Não transmite voz ao vivo em grupo.** `GroupPttProtocol` é o esqueleto de um protocolo de sinalização (claim/release/heartbeat). O `GroupPttFloor` oferece controle **local** do microfone. Ainda não existe o adaptador que transporta, autentica e distribui esses sinais entre telefones, nem recepção de pacotes de áudio em streaming. Assim, dois telefones sem ligação podem gravar ao mesmo tempo; o app exibe esse limite.

A primeira versão do futuro protocolo usa pacotes com magic/version/tipo/grupo/emissor/turno/timestamp/lease. Valida formato e duração; `acceptRemote` exige que a camada de rede forneça o **emissor autenticado**, nunca confia no emissor declarado pela mensagem. Evita replay, frames enormes, turnos vencidos e não-membros. Isso é arquitetura de base, não promessa de exclusividade global: redes com partições não têm trava de rádio perfeita sem reconciliação.

## Identidade de pacote Android

Mantido `app.getknit.knit.radiomesh` para facilitar atualizar a instalação REV4 sem perder dados locais. Alterar `applicationId` criaria um aplicativo independente, sem compartilhar os dados do app anterior.

## Como validar

- Testes básicos sem Android SDK: `kotlinc app/src/main/java/app/getknit/knit/ui/radiohub/GroupPttProtocol.kt scripts/nos_rev5_core_test.kt -include-runtime -d /tmp/nos-core.jar && java -jar /tmp/nos-core.jar`
- Verificações estáticas: `python scripts/check_nos_rev5.py` e `python scripts/check_radio_rev4.py`.
- Build Android (Android SDK, JDK 21 e Gradle 9.7.1): `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
- Para confirmar áudio offline, testar em pelo menos três aparelhos Android com Wi-Fi e dados móveis desligados, Bluetooth/Wi-Fi Aware ativos e rotas Mesh estabelecidas; repetir com desconexão e reconexão.

## Próximo passo técnico

1. Definir e negociar uma capacidade `CAP_GROUP_PTT` entre todos os participantes, com rejeição de versões antigas.
2. Integrar controle de piso autenticado por criptografia de grupo, com limites por participante, expiração, congestionamento e teste de corrida.
3. Implementar transporte efêmero de pacotes Opus com limites de latência e proteção contra áudio atrasado, sem colocar streaming na fila durável de anexos.
4. Adicionar reprodução de áudio em grupo, prioridade do locutor e testes em hardware.
