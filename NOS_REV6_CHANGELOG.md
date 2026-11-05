# NÓS REV6 — Rádio ao vivo separado de Mensagens

## Implementado
- Abas **Mensagens** e **Rádio** na navegação principal.
- Modo **Rádio ao vivo por Wi-Fi LAN** independente: dois ou mais aparelhos no mesmo Wi-Fi ou hotspot compatível, nome/senha de canal, PTT segurar para falar, recepção enquanto outro transmite, indicação de peers, duração máxima por transmissão, expiração de turnos, liberação ao sair/background.
- Datagramas UDP multicast e fallback broadcast local; AES-256-GCM, derivação de chave, sequências, proteção contra tampering/replay e piso local best-effort.
- Preservados canal gravado com prévia, rajadas gravadas em grupo e chamadas individuais do Knit; protocolo e banco anteriores intocados.
- Identidade visual atualizada com a segunda logo NÓS neon, redesenhando os ícones Android.
- Testes de unidade de codec e atualização de testes da Central; permissão CHANGE_WIFI_MULTICAST_STATE adicionada.

## Não implementado / não certificado
- **Rádio ao vivo multi-hop via Knit / Wi-Fi Direct automático / Bluetooth ou LoRa:** não disponível nesta REV6.
- **Grupo de rádio administrado por membros Knit:** a senha do canal é a única credencial de acesso; não há aprovação individual por contato.
- **APK:** build Android não reproduzido por falta de SDK/Gradle. Codec testado em JVM; rede multicast testada em duas sockets Linux; testes de voz Android precisam ser feitos com aparelhos físicos.

## Limitação deliberada
Não mostrar 'aparelhos conectados' se o telefone apenas abriu o socket; a contagem só aumenta após datagrama AES-GCM válido decodificado. Entrega de voz e alcance nunca são garantidos.

- A REV6 incrementou o `knit.versionCode` de 38 para 39 para permitir atualização Android quando a assinatura APK for mantida.
