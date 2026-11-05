# NÓS REV6 — Mensagens + Rádio, separados

Fork Android do **Knit 2.10.2**. Código-fonte **GPL-3.0-or-later**, mantendo arquivos COPYING, avisos e atribuições do projeto original.

## 1. Mensagens (estilo mensageiro)

A aba **Mensagens** conserva as conversas, grupos, anexos, chamadas individuais, notas de voz com prévia, mensagens locais/offline e transporte Mesh oferecidos pelo Knit. **Não há conexão com a rede nem com contas do WhatsApp**. Nada nessa parte foi substituído pelo módulo experimental de rádio ao vivo.

## 2. Rádio (PTT)

A aba **Rádio** oferece três caminhos diferentes:

- **Rádio ao vivo · Wi-Fi local (novo na REV6):** vários Androids na **mesma rede Wi-Fi LAN ou hotspot que encaminhe multicast/broadcast** entram com o mesmo nome de canal e senha. Cada pessoa escuta o canal e segura o botão para transmitir voz em tempo quase real. Não depende de dados móveis nem Internet. A sessão termina ao sair da tela ou deixar o app.
- **Walkie-talkie com contato:** chamada individual do motor Knit e sua rede Mesh, como nas REV2–REV3.
- **Áudios gravados em grupo:** o modo REV5 **Apertar e enviar** continua enviando áudio curto como mensagem criptografada de grupo, além do modo normal de gravação com prévia e envio manual. Podem ser entregues após reconexão.

### Protocolo de voz ao vivo REV6

`LanRadioWire.kt`: frames `HELLO`, `CLAIM`, `AUDIO`, `RELEASE` (versão e tamanho limitados), número de sequência por sessão, rejeição de replay, AES-256-GCM por pacote e chave PBKDF2-HMAC-SHA256 a partir de senha do canal. `LanRadioEngine.kt`: transporte UDP IP multicast com cópia em broadcast como fallback, áudio PCM 16 kHz mono 16 bits em quadros de 40 ms, máximo 30 s por aperto, reprodução enquanto outros transmitem, contagem de peers autenticados pela senha, prioridade local para colisões, expiração do turno. `LanRadioScreen.kt`: composição com entrada no canal, senha, estado, permissão de microfone, pressionar/soltar e sair.

**Limitações importantes:**

- O canal ao vivo **não trafega pela malha multi-hop do Knit** e não usa LoRa. A rede Wi-Fi pode não repassar multicast/broadcast; alguns hotspots bloqueiam dispositivos clientes entre si. Distância depende do alcance Wi-Fi e do ambiente.
- A senha compartilhada protege os datagramas, mas **não autentica membros do grupo do Knit** nem garante identidade de quem está falando. Qualquer pessoa com a senha pode participar; não reutilizar uma senha fraca. O protocolo é experimental e ainda necessita auditoria de segurança.
- Sem infraestrutura adicional não é possível transmitir numa frequência de rádio arbitrária com o Android. A voz ao vivo em Bluetooth/Wi-Fi Direct/mesh multi-hop requer implementação e testes adicionais. Para LoRa, é necessário rádio externo; o Meshtastic usa principalmente pacotes de baixa taxa.
- PTT é **half duplex**. Na presença de partições da rede, podem ocorrer colisões entre transmissores. Áudio de rádio ao vivo é efêmero, não gravado. Pode haver jitter/perda.
- A aplicação precisa permanecer em primeiro plano para este canal de áudio ao vivo. Sem sistema Android completo neste ambiente, não houve compilação do APK nem teste de 2–3 celulares; não chamar de homologado.

## Como testar em celulares reais

1. Compile o APK no Android Studio/CI com Android SDK, JDK 21 e Gradle 9.7.1, ou obtenha o artefato após um workflow bem-sucedido do GitHub Actions.
2. Instale **a mesma revisão** do NÓS em dois ou três celulares. Coloque todos na mesma rede Wi-Fi, ou conecte ao hotspot de um celular **sem Internet**; ative tráfego entre clientes no roteador se necessário.
3. Abra **Rádio → Rádio ao vivo**, use o mesmo canal e a mesma senha forte (10+ caracteres). Confira se cada aparelho encontra os outros (contagem de peers acima de zero).
4. De preferência com fones para evitar realimentação, segure o PTT em A e escute em B; repita no sentido contrário. Teste dois transmitindo ao mesmo tempo, áudio interrompido, perda de Wi-Fi e saída do app.
5. Abra **Mensagens → grupo** para conferir que os áudios com prévia e as rajadas da REV5 ainda funcionam, inclusive sem conexão imediata (fila Mesh). Teste chamadas individuais separadamente.
6. Faça testes com Android 10–16, redes 2,4 GHz/5 GHz e pelo menos dois roteadores diferentes; registre perda de pacotes, atraso de voz, compatibilidade e consumo de bateria.

## Testes disponíveis no fonte

- `kotlinc app/src/main/java/app/getknit/knit/ui/radiohub/LanRadioWire.kt scripts/nos_rev6_wire_test.kt -d /tmp/nos-rev6-tests.jar && kotlin -classpath /tmp/nos-rev6-tests.jar app.getknit.knit.ui.radiohub.Nos_rev6_wire_testKt`
- `python scripts/check_nos_rev6.py`
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — **não reproduzido** neste ambiente por indisponibilidade do Gradle/Android SDK.

## Identidade

A logo NÓS Neon 3D enviada pelo usuário foi incorporada como wordmark e ícones Android, sem mudar o `applicationId=app.getknit.knit.radiomesh` da REV5; a intenção é atualizar preservando os dados locais do app anterior.

## Próximas evoluções propostas

1. Teste real multiaparelhos, QoS, jitter buffer e conexão Wi-Fi Direct/Aware sem roteador, com fallback universal.
2. Canais persistentes com convite seguro, chave de canal negociada entre contatos, autenticação individual e controles de administração.
3. Transporte de voz por múltiplos saltos só depois de validar capacidade, latência, congestionamento e autenticação no Mesh. Adaptadores externos de rádio de longo alcance independentes do modo Wi-Fi local.
