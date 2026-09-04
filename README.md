# LembraAqui

[baixe aqui.](https://github.com/fabioqueiroz1415/LembraAqui/releases/download/v1.0.1/app-debug.apk)

Aplicativo Android nativo de lembretes baseados em lugares. O usuário cadastra uma área por latitude, longitude e raio e associa lembretes para **Chegando**, **No local** e **Saindo**. Os dados ficam somente no aparelho; não existe login, servidor ou backend.

## Compatibilidade

- **Android mínimo:** Android 7.0 / API 24.
- **compileSdk / targetSdk:** API 37.
- **JDK de build solicitado pelo projeto:** Java 25.
- **Gradle:** 9.6.0.
- **Android Gradle Plugin:** 9.4.0.
- O Gradle roda com JDK 25. O bytecode Android é configurado para Java 17, que é a configuração segura da toolchain Android atual e mantém compatibilidade com aparelhos antigos por meio do desugaring. Isso não reduz o requisito do ambiente de build em Java 25.

## Funcionalidades

- Cadastro, edição, pausa e exclusão de lugares.
- Localização por **Usar minha localização atual** ou **Colar coordenadas**. Não existe opção de colar link do Google Maps.
- Parser para coordenadas como `-12,1231234, -47,9238498234`, com ou sem espaços, além de validação de latitude e longitude.
- Raio de 50 m, 100 m, 200 m ou personalizado; 100 m é o padrão.
- Vários lembretes por lugar e vários lembretes do mesmo tipo.
- **Chegando:** processado ao entrar na geofence.
- **No local:** cada lembrete possui seu próprio tempo de permanência. A saída cancela os trabalhos pendentes e uma nova entrada reinicia a contagem.
- **Saindo:** processado ao deixar a geofence.
- Restrição por dias da semana.
- Restrição por intervalo de horário, inclusive intervalos que atravessam a meia-noite, como 22:00–06:00.
- Repetição: Uma vez, Toda vez e Uma vez por dia.
- Pausa independente de lugar e de lembrete.
- Reativar um lembrete **Uma vez** já concluído arma uma nova ocorrência e limpa o estado de execução anterior.
- Histórico local de entradas, saídas, notificações e problemas relevantes.
- Tema claro/escuro via Material 3.
- Tela de permissões com explicações em linguagem de usuário.
- Restauração do monitoramento após reinicialização e após atualização do aplicativo.
- Ferramentas de simulação de Chegando / No local / Saindo apenas no build `debug`.

## Como o monitoramento funciona

Existe **uma geofence por lugar**, não uma geofence por lembrete. Ela usa `GeofencingClient` do Google Play Services com ENTER e EXIT. Quando há lembretes de permanência, DWELL é adicionado como sinal auxiliar usando o menor tempo configurado no lugar.

Ao receber ENTER, o app registra o ciclo atual, processa lembretes Chegando e agenda um `WorkManager` independente para cada lembrete No local. Ao receber EXIT, todos os trabalhos de permanência daquele lugar são cancelados antes de processar os lembretes Saindo. Assim, um mesmo lugar pode ter, por exemplo, permanências de 5, 30 e 50 minutos sem desperdiçar três geofences.

Para oscilações na borda, eventos repetidos no mesmo estado são ignorados e uma reentrada muito rápida reutiliza o mesmo `cycleId`. Isso mantém a saída real válida — importante para cancelar permanências — sem permitir que uma oscilação ENTER/EXIT/ENTER dispare repetidamente lembretes configurados como “Toda vez”.

## Banco local

Room possui três entidades principais:

- `PlaceEntity`: nome, coordenadas, raio, ativo, estado de presença e ciclo.
- `ReminderEntity`: tipo, mensagem, permanência, dias, horários, repetição, ativo e estado de execução.
- `HistoryEntity`: somente eventos relevantes; não existe rastreamento contínuo de posições.

O banco começa na versão 1. Não é usado `fallbackToDestructiveMigration`. Ao alterar o schema em versões futuras, adicione uma migration explícita e preserve os arquivos de schema gerados em `app/schemas`.

## Privacidade

- `android:allowBackup="false"`.
- Regras de extração também excluem banco e arquivos de backup/transferência de dispositivo em versões recentes do Android.
- Não existe código de servidor, conta, login ou upload de coordenadas.
- O histórico não armazena percurso GPS; somente lugares cadastrados e eventos necessários ao funcionamento.

## Permissões

O fluxo não solicita tudo de uma vez:

1. Localização aproximada/precisa é solicitada primeiro. Geofencing confiável exige localização precisa.
2. No Android 10, a localização em segundo plano pode ser solicitada em uma etapa separada.
3. No Android 11+, a tela orienta o usuário a abrir as configurações do app e selecionar acesso à localização “o tempo todo”.
4. No Android 13+, `POST_NOTIFICATIONS` é solicitado separadamente.
5. Se a localização do sistema estiver desligada, há atalho para a configuração correspondente.

Se notificações estiverem desativadas, o evento é registrado no histórico, mas um lembrete “Uma vez” **não é consumido como executado com sucesso**.

## Reinicialização do aparelho

`BootReceiver` atende `BOOT_COMPLETED` e `MY_PACKAGE_REPLACED`. Em um reboot real, o estado persistido “dentro” é zerado antes de registrar novamente as geofences, pois o aparelho pode ter mudado de lugar enquanto estava desligado. Se o usuário estiver dentro de uma área após o boot, o `INITIAL_TRIGGER_ENTER` restabelece o ciclo corretamente.

## Abrir no Android Studio

1. Extraia o projeto.
2. Abra a pasta `LembraAqui` no Android Studio.
3. Garanta que o Android SDK da API 37 esteja instalado e que exista um JDK 25 disponível para o Gradle.
4. Sincronize o projeto e execute o módulo `app`.

O projeto não inclui `local.properties` com caminho pessoal. O Android Studio normalmente cria esse arquivo automaticamente para a máquina atual.

## Compilar pelo terminal

No Ubuntu/Linux:

```bash
./gradlew test assembleDebug
```

APK esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Na primeira execução, o script `gradlew` baixa a **distribuição oficial Gradle 9.6.0** por `services.gradle.org`, valida o SHA-256 fixado no projeto e usa essa distribuição para gerar localmente o `gradle-wrapper.jar` oficial. Isso evita depender de uma URL direta de `wrapper.jar` que não existe. Nas execuções seguintes, o Gradle já baixado fica em cache.

Se o SDK estiver instalado mas o terminal não souber onde ele está, configure `ANDROID_HOME`/`ANDROID_SDK_ROOT` ou deixe o Android Studio gerar `local.properties`. Nunca versione um `local.properties` com caminho pessoal.

## Testar geofencing sem caminhar

Em build `debug`:

1. Abra um lugar.
2. Toque em **Ferramentas de teste**.
3. Use **Simular CHEGANDO**, **Simular NO LOCAL** e **Simular SAINDO**.

A simulação passa pelo mesmo processador de regras, histórico e notificações da funcionalidade real. A tela não é oferecida no fluxo do build `release`.

Também existem testes unitários em `app/src/test` para:

- parser e validação de coordenadas;
- horário normal e intervalo atravessando meia-noite;
- dias da semana;
- Uma vez, Toda vez e Uma vez por dia;
- lembrete inativo;
- duplicidade por ciclo;
- entrada/saída rápida e proteção contra oscilação de borda.

## Limitações reais do Android

Geofencing não é um mecanismo de precisão de segundos. O disparo pode atrasar por Doze, economia de bateria, qualidade do GPS/rede, políticas do fabricante e comportamento do Google Play Services. `WorkManager` também pode adiar a execução de uma permanência sob restrições de energia. O aplicativo evita polling frequente de GPS justamente para manter baixo consumo.

O Android/Google Play Services limita a quantidade de geofences por aplicativo. O LembraAqui monitora até 100 lugares ativos e registra um aviso no histórico se houver mais. Um aparelho sem Google Play Services não oferece o `GeofencingClient` usado por este projeto. Depois de um “Forçar parada” manual nas configurações, o Android pode impedir recebimentos em segundo plano até o usuário abrir o app novamente.

## Estrutura principal

```text
app/src/main/java/com/lembraaqui/app/
├── data/                 Room, DAOs, entidades e Repository
├── domain/               parser, regras de horário/repetição e estado
├── background/           geofencing, receivers, WorkManager e processamento
├── ui/                   telas Compose e navegação
├── MainActivity.kt
├── MainViewModel.kt
├── PermissionStatus.kt
└── NotificationHelper.kt
```

## Build de release

O build `release` já habilita R8 e remoção de recursos. Para publicar em loja é necessário adicionar uma configuração de assinatura própria; nenhuma chave privada de assinatura deve ser incluída no repositório.
