# NFC Seguro

Projeto Android simples para leitura e organizacao local de tags NFC NDEF.

Este app nao clona credenciais de portaria, nao repete UID e nao emula cartoes de controle de acesso. Ele foi limitado a usos legitimos:

- ler mensagens NDEF;
- salvar um apelido, tecnologias detectadas e uma impressao SHA-256 local;
- gravar texto NDEF em tags gravaveis que voce possui.

## Como gerar APK depois

1. Abra esta pasta no Android Studio.
2. Aguarde o Gradle sincronizar.
3. Conecte o Samsung S22 com depuracao USB ativada ou use `Build > Generate Signed Bundle / APK`.
4. Para testar leitura, ative NFC no Android e aproxime uma tag NDEF.

## Observacao sobre Samsung S22 / NXP SN220U

Mesmo com hardware NFC compativel, o Android nao permite que apps comuns clonem UID, MIFARE Classic/DesFire protegidos ou credenciais proprietarias de portaria. Host Card Emulation no Android funciona com APDUs ISO-DEP de aplicacoes autorizadas, nao como repetidor universal de tags fisicas.
