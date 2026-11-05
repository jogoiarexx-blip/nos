# NÓS REV6 — Plano reproduzível de teste em celulares Android

> O código do rádio local foi desenvolvido, mas o teste automatizado em JVM **não certifica** que áudio UDP multicast/broadcast entre celulares funciona no campo. Esta página descreve os ensaios de aceitação. A compilação do APK ocorre em **Actions → NÓS Android — Compilar APK de teste**.

## Preparação

- Dois celulares Android 10+ (idealmente três), carregados, com o **mesmo APK NÓS REV6** instalado.
- Ambos conectados **à mesma rede Wi-Fi local** (ou a um hotspot que permita comunicação direta entre clientes). A internet pode ser desativada **depois** de baixar e instalar os APKs.
- Conceda permissão de microfone quando solicitada. Mantenha o app em primeiro plano e o display ligado para o primeiro teste.
- Posicione os aparelhos afastados ou utilize fones para evitar microfonia.
- Não use rede Wi-Fi de convidados/isolamento de clientes: essas redes frequentemente bloqueiam multicast e broadcast, mesmo quando os aparelhos indicam Wi-Fi conectado.

## Teste A — Mensagens e áudio gravado (não regressão)

1. Em dois aparelhos, crie perfis NÓS e estabeleça conexão pelo modo Mesh do Knit.
2. Envie texto e foto; confirme entrega e recebimento com a internet desligada.
3. Grave nota de voz tradicional, ouça a prévia e envie. Repita com mensagem de voz em grupo.
4. Se estiverem fora do alcance, valide entrega após reconexão em vez de esperar envio imediato.

**Esperado:** funções de chat e áudio gravado continuam independentes do rádio LAN.

## Teste B — Canal de rádio ao vivo via Wi-Fi local

1. Abra **Rádio → Rádio ao vivo (Wi-Fi local)** nos dois aparelhos.
2. Insira o mesmo nome de canal e a **mesma senha de 10 ou mais caracteres** e toque em Entrar.
3. Aguarde até 10 segundos; cada dispositivo deve exibir pelo menos 1 participante remoto autenticado.
4. Segure **Aperte para falar** no aparelho A durante 3 a 5 segundos; fale.
5. Confirme que B ouve enquanto A segura, sem aguardar o envio de arquivo. Solte, aguarde o turno liberar e repita de B para A.
6. Com três aparelhos, valide A transmitindo e B/C recebendo em paralelo.

**Esperado:** voz chega com atraso perceptível reduzido, sem arquivo de áudio gravado na conversa. O protocolo é HALF-DUPLEX e o controle de quem fala é **best effort**, podendo haver colisões em perdas/partições da rede.

## Teste C — Falhas e segurança

| Cenário | Resultado esperado |
|---|---|
| Nome de canal igual, senha diferente | Pacotes não são aceitos; não há áudio |
| Microfone negado | Apenas escuta; orienta conceder permissão |
| Dois usuários pressionam simultaneamente | Disputa pode ocorrer; nunca prometer exclusividade perfeita |
| Wi-Fi desligado ou alternando rede | Erro/conexão interrompida ou perda de pares; recuperação pode exigir sair e entrar |
| Tela fechada/app em segundo plano | Sessão para; não grava áudio escondido |
| Hotspot que bloqueia clientes | Rádio pode não funcionar; testar outro roteador |
| Mensagem de voz gravada | Continua salva no chat, independentemente do canal ao vivo |

## Coleta mínima para diagnóstico

Registre: marca/modelo e Android de cada aparelho, se estava em Wi-Fi de roteador ou hotspot, se o canal mostrou pares, se A→B e B→A transmitiram voz, se o áudio foi picotado, e os passos exatos da falha. Não publique senhas, mensagens privadas nem identificadores de pessoas.

## Limitações reais da REV6

- **Sem ligação com grupos WhatsApp.** É um mensageiro próprio inspirado na experiência de chat.
- O rádio LAN usa IP sobre Wi-Fi, com pacotes UDP criptografados. **Não** atravessa automaticamente múltiplos saltos pelo Knit Mesh, não faz streaming via Bluetooth e **não** transmite LoRa/FM/VHF/UHF pelo hardware do telefone.
- A senha compartilhada autoriza acesso ao canal, mas **não comprova quem é a pessoa** no canal; trocar senha é necessário após vazamento.
- Testes de unidade dos pacotes e sockets Linux não equivalem a certificação de comunicação entre celulares.
