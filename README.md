# FingerCapture

FingerCapture est un petit utilitaire Windows qui relie un lecteur d’empreintes compatible ZKTeco à un formulaire. Trois lectures du même doigt produisent un gabarit ZKTeco encodé en Base64 ; l’application le colle dans le champ sélectionné avec **Ctrl+V**, puis envoie **Entrée**. L’interface affiche les consignes, l’avancement et un aperçu temporaire des captures.

L’application fonctionne localement, indépendamment du site utilisé. Elle ne modifie pas la démonstration Java fournie avec le SDK.

## Démarrage rapide

Après avoir suivi la section [Installation](#installation), depuis la racine du dépôt `FingerCapture`, dans PowerShell :

```powershell
.\build.ps1
.\run.ps1 -NativeDirectory 'C:\chemin\vers\les\DLL\x64'
```

Si le JAR reste dans le dossier du SDK, remplacer la première commande par `./build.ps1 -SdkJarPath 'C:\chemin\vers\Java\lib\ZKFingerReader.jar'`.

Après le lancement, cliquer sur **Commencer**, puis sélectionner le champ du formulaire dans les cinq secondes. Présenter trois fois le même doigt, en le retirant entre les lectures. Les prérequis et les autres options de lancement sont détaillés ci-dessous.

Le nom du projet est **FingerCapture**. Les fichiers construits conservent actuellement leurs noms techniques `finger-enrollment-1.0.0.jar` et `finger-enrollment-1.0.0.zip`.

## Prérequis

- Windows avec une session utilisateur graphique.
- Lecteur compatible avec ce SDK, pilote et composants natifs ZKTeco installés depuis le fournisseur. Fermer la démonstration ou tout autre logiciel utilisant le lecteur.
- Java **17 ou supérieur**, de la même architecture que **libzkfp.dll et ses dépendances** : JVM x64 pour DLL x64, JVM x86 pour DLL x86. Le JAR Java seul ne contient pas ces composants natifs.
- Maven 3.9+ et un JDK pour compiler. Le poste client a seulement besoin de Java et des composants natifs.

Le JAR ZKTeco utilise JNI pour accéder à `libzkfp`. Ne pas confondre `libzkfp.dll` avec `libzkfpcsharp.dll`. Le dossier natif doit également contenir ou permettre de trouver les DLL dépendantes et les runtimes exigés par l’installation ZKTeco.

## Installation

### 1. Installer le pilote et les composants du SDK ZKTeco

**Oui, les composants natifs du SDK doivent être installés sur chaque poste qui utilise le lecteur.** Compiler le projet ou ajouter le JAR Java ne les installe pas. Pour développer, il faut aussi récupérer le SDK Java auprès de ZKTeco ou du fournisseur du lecteur.

Pour le paquet **ZKFinger Standard SDK 5.3.0.33**, l’installateur à lancer est **`setup.exe` à la racine du paquet**, intitulé « Fingerprint Reader Standard Driver Setup » (version `5.3.0.33`). C’est le point d’entrée pour installer les composants ZKTeco sur le poste client ; il n’est normalement pas nécessaire de copier les DLL à la main. Java reste un prérequis séparé.

| Élément | Poste de développement | Poste client utilisant le ZIP |
| --- | --- | --- |
| Lecteur compatible, pilote et composants natifs ZKTeco | Nécessaires pour tester le matériel | Nécessaires |
| Java 17 ou supérieur, compatible avec l’architecture des DLL | JDK nécessaire | Environnement Java avec Swing nécessaire |
| Maven 3.9+ | Nécessaire pour compiler | Inutile |
| `ZKFingerReader.jar` provenant du SDK Java | À fournir avant la compilation | Inclus dans le paquet construit |

1. Identifier le modèle exact du lecteur et obtenir le pilote Windows et le SDK compatibles auprès du fournisseur. Un capteur intégré reconnu par Windows Hello, par exemple un Goodix, n’est pas automatiquement compatible avec le SDK ZKTeco.
2. Exécuter l’installateur fourni pour le pilote et les composants natifs, en suivant les instructions du fournisseur. Installer également les runtimes qu’il demande ; redémarrer Windows si l’installateur le demande.
3. Brancher le lecteur selon les consignes du fournisseur et vérifier son état dans le **Gestionnaire de périphériques**.
4. Tester le lecteur avec la démonstration fournie dans le SDK, puis la fermer avant de lancer FingerCapture.

La présence de `libzkfp.dll` seule ne garantit pas une installation complète : le moteur biométrique et les dépendances du fournisseur sont également nécessaires. Utiliser un ensemble de composants de même architecture et compatibles entre eux. Ne pas remplacer des DLL système une par une pour contourner une installation incomplète.

### 2. Préparer Java, Maven et le JAR du SDK

Cette étape concerne la compilation depuis les sources. Avec un ZIP déjà construit, passer à [Installer sur un poste client](#installer-sur-un-poste-client).

Installer un JDK 17 ou supérieur et Maven 3.9+, puis vérifier dans un nouveau terminal PowerShell :

```powershell
java -version
javac -version
mvn -version
java -XshowSettings:properties -version
```

Vérifier `java.home` et `os.arch` dans la dernière commande : `amd64` correspond à une JVM x64, `x86` à une JVM 32 bits. Maven et VS Code peuvent utiliser une JVM différente ; contrôler également leurs réglages.

Le dépôt ne contient pas le JAR propriétaire du SDK. Indiquer au script son emplacement dans le SDK Java avec `-SdkJarPath` (garder les guillemets si le chemin contient des espaces) :

```powershell
.\build.ps1 -SdkJarPath 'C:\chemin\vers\ZKFinger Standard SDK 5.3.0.33\Java\lib\ZKFingerReader.jar'
```

Cette commande utilise directement le fichier du SDK, sans le déplacer. L’installation de `setup.exe` ne configure pas son emplacement pour la compilation.

Pour utiliser `./build.ps1` sans paramètre, le chemin par défaut reste **`../lib/ZKFingerReader.jar` par rapport au dossier du script**. On peut donc aussi préparer cette arborescence en copiant le fichier fourni dans le SDK Java :

```text
dossier-de-travail/
├── lib/
│   └── ZKFingerReader.jar
└── FingerCapture/
    ├── build.ps1
    ├── run.ps1
    ├── pom.xml
    └── src/
```

Le dossier `lib` est donc voisin de `FingerCapture`, pas à l’intérieur du dépôt. Si le dépôt se trouve déjà dans le dossier `Java` du SDK, conserver la même disposition relative. Les DLL natives ne vont pas dans ce dossier `lib` destiné au JAR.

### 3. Compiler et vérifier l’installation

Depuis la racine du dépôt :

```powershell
Test-Path ..\lib\ZKFingerReader.jar
.\build.ps1
.\run.ps1 -NativeDirectory 'C:\chemin\vers\les\DLL\x64' -CheckSdk
```

`Test-Path` doit retourner `True`. Remplacer le chemin d’exemple par le dossier réel contenant `libzkfp.dll` et les composants natifs compatibles avec Java. Si l’installation du fournisseur rend déjà les bibliothèques accessibles, utiliser simplement `./run.ps1 -CheckSdk`.

Le paramètre `-NativeDirectory` configure le chemin de recherche de Java et le `PATH` du processus lancé ; **il n’installe ni pilote ni SDK**.

Le diagnostic doit afficher :

```text
SDK chargé, lecteur ouvert et dimensions lues. Aucune empreinte capturée.
```

Il n’effectue aucune capture et n’envoie aucune touche. En cas de code `-1` (`ZKFP_ERR_INITLIB`), l’initialisation de la bibliothèque du moteur échoue : vérifier ou réparer les composants natifs avec l’installateur du fournisseur, puis relancer le diagnostic. Ce code seul ne prouve pas que le lecteur est absent. Voir aussi [Dépannage](#dépannage).

Après un diagnostic réussi, lancer `./run.ps1` avec le même `-NativeDirectory` si nécessaire, puis utiliser `example-form.html` pour vérifier les trois lectures et l’insertion.

### Installer sur un poste client

1. Installer Java 17 ou supérieur avec Swing, puis lancer `ZKFinger Standard SDK 5.3.0.33\setup.exe` pour installer les composants ZKTeco. Suivre les consignes de l’installateur et utiliser un lecteur compatible avec ce SDK.
2. Décompresser entièrement `target/finger-enrollment-1.0.0.zip`, produit par la compilation. Conserver le dossier `lib` du paquet à côté du JAR principal.
3. Ouvrir PowerShell dans le dossier décompressé et lancer :

```powershell
.\run.ps1 -NativeDirectory 'C:\chemin\vers\les\DLL\x64' -CheckSdk
.\run.ps1 -NativeDirectory 'C:\chemin\vers\les\DLL\x64'
```

Le poste client n’a besoin ni de Maven ni des sources. Le ZIP inclut les dépendances Java, mais pas Java lui-même, le pilote ou les DLL natives ZKTeco. Respecter les conditions de redistribution du fournisseur.

## Bibliothèques et fonctionnement technique

### Dépendances

| Composant | Version utilisée | Rôle |
| --- | --- | --- |
| Java / Swing | Java 17 minimum | Exécution, interface et aperçu de l’empreinte. |
| ZKFinger Reader SDK for Java | SDK à obtenir auprès du fournisseur, déclaré `5.3.0.33` dans Maven | Accès au lecteur, acquisition, comparaison et fusion des gabarits. |
| `ZKFingerReader.jar` | `com.zkteco.sdk:finger-reader:5.3.0.33` | Interface Java du SDK ; à placer dans `../lib/` puis installée dans le dépôt Maven local par `build.ps1`. |
| `libzkfp.dll` | Selon l’installation du fournisseur | Bibliothèque native chargée par le JAR ZKTeco via JNI ; elle et ses dépendances doivent être installées sur le poste client. |
| JNA et JNA Platform | `5.17.0` | Accès aux API Windows pour surveiller la fenêtre active et les touches de modification. |
| JUnit Jupiter | `5.11.4` | Tests automatisés ; dépendance utilisée uniquement lors des tests. |
| Maven | 3.9 ou supérieur | Compilation, tests et création du paquet. |

Les versions des dépendances Java sont définies dans [`pom.xml`](pom.xml). Le JAR du SDK et la bibliothèque native sont deux éléments distincts : la présence du JAR ne garantit pas que les DLL, leurs dépendances ou le pilote soient disponibles. Le script `build.ps1` installe le JAR ZKTeco localement sous les coordonnées Maven indiquées ; il ne télécharge ni n’installe le pilote ou les DLL du fournisseur.

### Parcours d’une capture

1. Après le compte à rebours, FingerCapture mémorise la fenêtre au premier plan. Une session démarre sur un thread de travail dédié.
2. `ZkReader` initialise le SDK, ouvre le premier lecteur disponible, initialise le moteur biométrique et lit les dimensions de l’image.
3. `FingerprintSensorEx.AcquireFingerprint` fournit une image en niveaux de gris et un échantillon de gabarit. `CaptureCycle` attend un retrait du doigt entre deux poses ; les résultats de lecture insuffisante ou occupée restent réessayables.
4. `DBMatch` compare les deuxième et troisième échantillons au premier. Après trois lectures acceptées, `DBMerge` produit le gabarit final à la longueur indiquée par le SDK.
5. Le gabarit est encodé en Base64, placé dans le presse-papiers, puis collé dans la fenêtre cible à l’aide de `java.awt.Robot`. Une touche **Entrée** est ensuite envoyée. L’application surveille la fenêtre au premier plan pendant l’opération et effectue des contrôles avant l’insertion.
6. Les ressources du lecteur et du moteur sont libérées ; les tableaux biométriques et l’aperçu sont effacés en mémoire dans la mesure permise par Java.

### Organisation du code

Tous les fichiers principaux se trouvent dans `src/main/java/com/zkteco/enrollment/` :

| Classe | Responsabilité |
| --- | --- |
| `EnrollmentApp` | Démarrage, compte à rebours et coordination des états de l’application. |
| `ProgressWindow` et `FingerprintPreview` | Fenêtre Swing, progression, messages et aperçu temporaire. |
| `EnrollmentController` et `EnrollmentSession` | Exécution d’une seule session, annulation, délai et orchestration des trois lectures. |
| `ZkReader` et `CaptureCycle` | Appels au SDK et gestion des poses successives du doigt. |
| `WindowsEvents`, `WindowsDesktop` et `GuardedPaste` | Surveillance de la fenêtre active, presse-papiers et insertion clavier contrôlée. |

Dans l’archive du SDK Java du fournisseur, consulter `Java/ZKFinger Reader SDK for JAVA_en_V2.pdf` et la démonstration dans `Java/sample/`, selon la version distribuée. Ces fichiers ne sont pas inclus dans ce dépôt.

## Compiler et tester

Les commandes suivantes sont lancées depuis la racine du dépôt `FingerCapture`, dans PowerShell :

```powershell
.\build.ps1
```

Le script installe le JAR indiqué par `-SdkJarPath` (par défaut `../lib/ZKFingerReader.jar`) dans le dépôt Maven local, compile, exécute les tests et prépare `target/finger-enrollment-1.0.0.zip`. Maven télécharge les dépendances Java et ses plugins au premier passage ; Maven lui-même doit déjà être installé. Les dépendances sont épinglées dans `pom.xml`.

Pour relancer uniquement les tests après cette première installation :

```powershell
mvn -B -ntp test
```

La compilation normale vérifie l’interface sans afficher de fenêtres sur le bureau. La vérification native du focus est un **test interactif séparé** : elle ouvre deux fenêtres de test et nécessite de ne pas changer de fenêtre pendant son exécution.

```powershell
mvn -B -ntp "-Denrollment.desktopTests=true" "-Dtest=ProgressWindowTest" test
```

Ce test mesure les mises à jour de progression après l’ouverture initiale de l’application, avec une fenêtre cible contrôlée. Si Windows refuse l’activation de cette cible ou qu’une autre application interrompt le test, celui-ci est déclaré non concluant (ignoré). Si la fenêtre d’enrôlement prend le focus pendant les mises à jour, le test échoue.

## Lancer

### Depuis VS Code

Ouvrir la racine du dépôt `FingerCapture` avec les extensions Java nécessaires, puis effectuer l’installation et la compilation décrites ci-dessus. Le dépôt ne fournit pas de configuration `.vscode/launch.json` ; le lancement depuis le terminal intégré avec `run.ps1` permet d’utiliser les paramètres natifs documentés.

Pour déboguer directement `EnrollmentApp.main`, configurer la JVM avec `-Djava.library.path=C:\chemin\vers\les\DLL\x64` et rendre les dépendances natives accessibles dans le `PATH` du processus. Ajouter l’argument `--check-sdk` pour effectuer uniquement le diagnostic sans capture ni touches simulées.

Si le projet avait déjà été ouvert sans ses dépendances, importer le projet Maven après l’installation du JAR SDK dans le dépôt local. Au besoin, lancer **Java: Clean Java Language Server Workspace** depuis la palette de commandes, puis accepter le rechargement. Ne pas poursuivre un lancement avec des erreurs de compilation.

### Depuis PowerShell

```powershell
.\run.ps1 -NativeDirectory 'C:\chemin\vers\les\DLL\x64'
```

Si les bibliothèques sont déjà accessibles par le chemin de recherche de Java et le `PATH`, omettre `-NativeDirectory`. Pour sélectionner une autre JVM :

```powershell
.\run.ps1 -NativeDirectory 'C:\ZKTeco\x64' -JavaHome 'C:\Program Files\Java\jdk-21'
```

Une **petite application Swing** s’ouvre en haut à droite de l’écran, avec les boutons **Commencer** et **Quitter**. Elle peut être déplacée, redimensionnée ou réduite, et reste au-dessus du navigateur lorsqu’elle est affichée. Elle affiche une consigne à la fois, l’aperçu de l’empreinte et le compteur de lectures acceptées (0 à 3). Les messages longs peuvent défiler sans masquer les boutons. Les mises à jour de progression ne demandent pas le focus au formulaire.

Le lancement normal utilise `javaw.exe` : aucun terminal de logs n’est nécessaire et vous pouvez fermer le terminal ayant lancé le script. **Fermer la fenêtre Swing ou cliquer sur Quitter arrête l’utilitaire.** Pour afficher les logs lors d’un dépannage, utiliser `run.ps1 -Console` (compatible avec `-NativeDirectory` et `-JavaHome`). `-CheckSdk` conserve toujours la sortie dans le terminal.

La fenêtre affiche également un aperçu en niveaux de gris de chaque nouvelle capture réussie, comme la démonstration du SDK, avec des proportions conservées. Le compteur indique séparément les lectures acceptées après comparaison du doigt. Retirez le doigt entre deux lectures. L’aperçu reste uniquement en mémoire et est effacé à la fin de l’opération, lors d’une annulation ou à la fermeture ; aucun fichier image n’est créé.

Le résultat textuel reste affiché jusqu’à l’opération suivante. L’état initial indique que le lecteur n’a pas encore été testé : son ouverture est vérifiée à chaque enrôlement. La zone de notification n’est plus nécessaire.

Diagnostic sans capture ni touches simulées, si les DLL sont déjà accessibles :

```powershell
java -jar .\target\finger-enrollment-1.0.0.jar --check-sdk
```

Ou avec les mêmes paramètres natifs que le lancement normal :

```powershell
.\run.ps1 -NativeDirectory 'C:\ZKTeco\x64' -CheckSdk
```

Pour distribuer, décompresser le ZIP sur le PC client et lancer son `run.ps1` avec les mêmes paramètres. Le paquet contient le JAR, ses dépendances, ce guide et le formulaire de test ; le pilote, Java et les DLL du fournisseur restent des prérequis séparés. Vérifier les conditions de redistribution du SDK avant diffusion.

## Utilisation

1. Cliquer sur **Commencer** dans l’utilitaire.
2. Pendant les **5 secondes** du compte à rebours, cliquer dans le champ du navigateur destiné au gabarit.
3. Présenter trois fois **le même doigt**, en le retirant entre les lectures. Le lecteur doit constater une absence de doigt entre deux acquisitions.
4. Garder la fenêtre et le champ sélectionnés pendant toute l’opération.
5. Après fusion, l’utilitaire remplace le presse-papiers, colle le Base64 et envoie une seule touche Entrée. **Cette touche peut soumettre le formulaire.**

Une seule pose du doigt ne remplit donc pas encore le champ : les **trois lectures acceptées** sont nécessaires pour l’enrôlement. Chaque lecture acceptée met à jour le compteur de la fenêtre. En mode `-Console`, les messages restent aussi disponibles dans le terminal avec le préfixe `[enrolement]`. Si l’extraction est insuffisante (SDK −9) ou si la capture est déjà en cours (SDK −12), l’opération continue dans la même session, avec le même délai maximal. Ces retours ne comptent pas comme un retrait du doigt.

Le bouton **Annuler** arrête l’opération. Le délai est de **60 secondes**, ouverture du lecteur comprise. Une opération annulée garde le lecteur réservé jusqu’au retour des appels natifs et à leur nettoyage ; le bouton reste désactivé pendant cette libération. Une fois le bouton **Commencer** réactivé, une nouvelle opération est possible, y compris après reconnexion USB. Cette version se commande par les boutons de la fenêtre ; elle n’enregistre pas de raccourci clavier global.

La surveillance Windows mémorise un changement de fenêtre, même si l’opérateur revient ensuite au navigateur. Aucun collage n’est lancé après une annulation, une erreur, un délai dépassé ou un changement de fenêtre constaté. Si le changement intervient après le collage, l’utilitaire bloque Entrée autant que possible ; le texte déjà collé reste dans le champ. L’injection de touches et le changement de fenêtre ne constituent pas une opération atomique Windows.

Le programme ne sait pas distinguer deux champs d’un même navigateur : ne pas changer d’onglet, de champ ou de page pendant la capture. Le champ doit autoriser Ctrl+V et accepter le Base64 sans le tronquer. L’utilitaire et le navigateur doivent s’exécuter avec des droits compatibles ; lancer les deux normalement, sans élévation.

Pour quitter, cliquer sur la **croix de fermeture de la fenêtre** ou sur **Quitter**. La fermeture arrête la surveillance des fenêtres, annule la capture et libère le SDK sur son thread de travail. Un appel natif bloqué ne peut pas être interrompu de force sans risque : le délai interdit toute insertion tardive, mais la libération attend le retour du pilote. Cliquer dans la fenêtre de l’utilitaire pendant une capture change la fenêtre active et annule l’insertion ; garder le curseur dans le formulaire jusqu’à la fin.

## Données produites

Le champ reçoit uniquement le gabarit final issu de `DBMerge`, encodé en Base64 sans saut de ligne, en respectant la longueur retournée par le SDK. Ce n’est ni une image ni un format biométrique universel. L’application web peut le conserver avec la personne et devra utiliser un moteur compatible ZKTeco pour une reconnaissance future. Base64 n’est pas un chiffrement.

Aucune image ou empreinte n’est enregistrée dans un fichier ou les journaux par cet utilitaire. Les tampons biométriques sont effacés après usage dans la mesure permise par Java. Le texte reste dans le champ et le presse-papiers ; l’historique ou la synchronisation du presse-papiers Windows peuvent aussi le conserver selon la configuration du poste.

## Vérification avec le matériel

Ouvrir `example-form.html` localement dans le navigateur. Aucun serveur n’est nécessaire et aucune donnée n’est transmise par cette page.

Le message **« Validation 1 : gabarit reçu (…) »** confirme une première validation du formulaire. La page intercepte volontairement l’envoi et reste affichée ; elle ne navigue pas vers une autre page. Si aucun clic sur le bouton n’a été effectué, cette validation provient de la touche Entrée envoyée automatiquement.

- Réussite : trois lectures distinctes, état « Validation 1 », longueur binaire non nulle, aucun second envoi automatique.
- Répétition : une deuxième opération donne « Validation 2 », sans interférence avec la précédente.
- Annulation, mauvais doigt, délai dépassé et débranchement : aucune nouvelle validation ni modification du champ avant une éventuelle insertion déjà commencée.
- Changement de fenêtre pendant la capture, puis retour : aucun collage ; recommencer avec **Commencer** puis sélectionner à nouveau le champ pendant le compte à rebours.
- Fenêtre Swing visible, sans console : progression 0/3, 1/3, 2/3, 3/3 sans perdre le focus du champ ; collage et validation automatiques. Vérifier aussi une erreur et une annulation dans la fenêtre.
- Croix de fermeture et bouton Quitter : la fenêtre disparaît et le lecteur est libéré après retour des appels natifs.
- Rebrancher le lecteur après une erreur et recommencer.
- Contrôler le comportement avec le formulaire réel, notamment ses événements de collage, sa limite de longueur et son action sur Entrée.

Les tests automatisés simulent le lecteur et les sorties clavier : ils couvrent le parcours, les erreurs, la concurrence, la longueur du gabarit, les garde-fous d’insertion, le compteur de progression, les messages et les demandes d’arrêt via la croix et le bouton Quitter. Le test interactif optionnel contrôle en plus la conservation du focus lors des mises à jour. Ils ne valident pas le matériel, la qualité biométrique, le pilote, les délais de collage d’un site particulier ou la compatibilité du futur moteur de reconnaissance.

## Dépannage

- **Lecteur déjà utilisé** : fermer les autres instances de l’utilitaire et la démonstration du SDK avant de recommencer.
- **SDK indisponible** : vérifier les DLL, leurs dépendances et l’architecture de Java (`java -XshowSettings:properties -version`), puis **redémarrer l’utilitaire** après correction.
- **Initialisation du SDK, code −1** : selon la documentation du fournisseur, la bibliothèque d’algorithme ne s’initialise pas. Vérifier ou réparer l’installation ZKTeco et les dépendances natives ; ce code ne permet pas à lui seul de conclure que le lecteur est absent.
- **Aucun lecteur** : vérifier le câble USB et le pilote dans le Gestionnaire de périphériques, puis recommencer.
- **Presse-papiers occupé** : aucune touche n’est envoyée si la copie échoue ; fermer le logiciel qui monopolise le presse-papiers et recommencer.
- **Fenêtre réduite** : la restaurer depuis la barre des tâches avant de démarrer la capture.
- **Logs nécessaires** : relancer avec `run.ps1 -Console`.

Références techniques : API Windows de JNA et documentation PDF distribuée avec le SDK Java du fournisseur.
