# DAI - Practical Work 3 : airAPI
![Static Badge](https://img.shields.io/badge/HEIG--VD-labo-red?logo=intellijidea)
![Maven](https://img.shields.io/badge/build-Maven-blue?logo=apachemaven)
![Java](https://img.shields.io/badge/java-21-orange?logo=openjdk)
![TraefikProxy](https://img.shields.io/badge/Traefik-%252300314b.svg?style=for-the-badge&logo=traefikproxy&logoColor=white)

## Table des matières

- [Clone et Build](#clone-et-build)
- [API](#api)
- [Infrastructure](#infrastructure)
  - [Création et Initialisation de la VM](#création-et-initialisation-de-la-vm)
  - [Zone DNS](#zone-dns)
  - [DNS records](#dns-records)
- [Charger l'application sur la VM](#charger-lapplication-sur-la-vm)
- [Gestion du cash](#gestion-du-cache)
  - [Endpoint Company](#endpoint-company)
  - [Endpoint Avion](#endpoint-avion)
- [Auteurs](#auteurs)

## Clone et Build

Les étapes suivantes vous permettent de cloner et builder le projet afin de pouvoir commencer à l'utiliser. Nous utilisons Maven comme gestionnaire de projet.

Cloner le repo :
```bash
git clone git@github.com:QuentinMichon/dai-work-3.git
```

Entrer dans le dossier racine :
```bash
cd dai-work-3
```

### Pour Linux / macOS

Télécharger les dépendances :
```bash
./mvnw dependency:go-offline
```

Générer une archive JAR :
```bash
./mvnw clean package
```

### Pour Windows

Télécharger les dépendances :
```bash
./mvnw.cmd dependency:go-offline
```

Générer une archive JAR :
```bash
./mvnw.cmd clean package
```

> [!NOTE]
> Si vous utilisez l'IDE IntelliJ IDEA, vous pouvez exécuter la configuration **Package as JAR file**.
> Cela automatise la création de l'archive, ainsi que d'autres configurations permettant de lancer une instance de serveur ou client en local.

## API

La description de l'API se trouve [ici](./doc/API/README.md).

## Infrastructure

Dans ce chapitre, nous détaillons comment nous avons monté l'infrastructure pour faire tourner notre API sur le web.

### Création et Initialisation de la VM

Nous avons utilisé Microsoft Azure pour héberger notre machine virtuelle. Pour l'étape de création, nous avons suivi les instructions du cours DAI dispensé par la HEIG-VD.

Les chapitres suivants montrent les étapes que nous avons réalisées :
- [Obtain a virtual machine on a cloud provider](https://github.com/heig-vd-dai-course/heig-vd-dai-course/blob/main/11.03-ssh-and-scp/01-course-material/README.md#obtain-a-virtual-machine-on-a-cloud-provider)
- [Access and configure the virtual machine](https://github.com/heig-vd-dai-course/heig-vd-dai-course/blob/main/11.03-ssh-and-scp/01-course-material/README.md#access-and-configure-the-virtual-machine)

>[!NOTE] 
>L'IP de notre VM est `51.103.154.140`

### Zone DNS

Nous avons utilisé un nom de domaine hébergé chez Hostpoint : `swisspotter.ch`
Ensuite, nous avons realisé deux enregistrements de type A pour pointer un sous-domaine vers l'IP de notre VM :
- `dai.swisspotter.ch A 51.103.154.140` pour faire pointer le sous-domaine dai.swisspotter.ch sur la VM
- `*.dai.swisspotter.ch A 51.103.154.140` pour faire pointer tous les sous-domaines de dai.swisspotter.ch sur la VM

### DNS records

Voilà l'ensemble des DNS records présents sur notre zone DNS :

| Record 						| 	TTL		| Type 		| Valeur 										|	
|-------------------------------|-----------|-----------|-----------------------------------------------|
| swisspotter.ch 				| 3600 		| SOA 		| ns.hostpoint.ch hostmaster.hostpoint.ch ... 	|
| swisspotter.ch 				| 3600 		| NS 		| ns3.hostpoint.ch 								|
| swisspotter.ch 				| 3600 		| NS 		| ns2.hostpoint.ch 								|
| swisspotter.ch 				| 3600 		| NS 		| ns.hostpoint.ch 								|
| swisspotter.ch 				| 3600 		| MX 		| 10 mx2.mail.hostpoint.ch 						|
| swisspotter.ch 				| 3600 		| MX 		| 10 mx1.mail.hostpoint.ch 						|
| `*.swisspotter.ch`			| 3600 		| MX 		| 10 mx2.mail.hostpoint.ch 						|
| `*.swisspotter.ch` 			| 3600 		| MX 		| 10 mx1.mail.hostpoint.ch 						|
| autoconfig.swisspotter.ch 	| 300 		| CNAME 	| autoconfig.mail.hostpoint.ch 					|
| autodiscover.swisspotter.ch 	| 300 		| CNAME 	| autoconfig-nonssl.mail.hostpoint.ch 			|
| lists.swisspotter.ch 			| 300 		| CNAME 	| lists.admin.hostpoint.ch 						|
| swisspotter.ch 				| 300 		| A 		| 151.101.192.119 								|
| swisspotter.ch 				| 300 		| A 		| 151.101.128.119 								|
| **\*.dai.swisspotter.ch** 	| **300** 	| **A** 	| **51.103.154.140** 							|
| **dai.swisspotter.ch** 		| **300** 	| **A** 	| **51.103.154.140** 							|
| www.swisspotter.ch 			| 300 		| A 		| 151.101.128.119 								|
| www.swisspotter.ch 			| 300 		| A 		| 151.101.192.119 								|
| swisspotter.ch	 			| 300 		| TXT 		| "v=spf1 redirect=spf.mail.hostpoint.ch" 		|

Les lignes en gras sont les records ajoutés pour atteindre notre VM avec le sous-domaine `dai.swisspotter.ch`.

## Charger l'application sur la VM

Pour commencer, connectez-vous en SSH à la VM :
```bash
ssh ubuntu@dai.swisspotter.ch
```

Ensuite, sur la VM, générez une clé SSH et déposez-la dans vos clés SSH sur GitHub. Voir la [documentation officielle](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent) pour vous aider.

Une fois les réglages effectués, clonez le repo :
```bash
git clone git@github.com:QuentinMichon/dai-work-3.git
```

Déplacez-vous dans le dossier `traefik` :
```bash
cd dai-work-3/traefik
```

Ensuite lisez [le guide](./traefik/README.md) pour lancer Traefik sur la VM.

Une fois Traefik lancé, revenez à la racine du projet `dai-work-3` et renommez le fichier `.env.exemple` en `.env`, puis remplissez le champ `API_FULLY_QUALIFIED_DOMAIN_NAME` avec le nom de domaine qui pointe sur votre VM (dans notre cas : `dai.swisspotter.ch`).

```bash
# Renommer le fichier .env.exemple en .env
mv .env.exemple .env

# Ouvrir l'éditeur de fichier pour modifier le fichier
nano .env
```

Ensuite, il faut générer l'archive JAR du projet (voir les commandes dans [Clone et Build](#clone-et-build)).

Une fois les modifications effectuées et après avoir généré l'archive du projet, lancez le container avec Docker Compose :
```bash
  docker compose up airapi --build
```

Le service est accessible !

## Gestion du cache
### Endpoint Company
Nous avons créé un système de cache basé sur le model par `validation` en utilisant les headers des requêtes. Dans notre cas, c'est le client qui stock le cache.
Le Serveur utilise `Last-Modified` à chaque `GET` de la part du client pour indiquer la date de la dernière modification des données.
Si le client veut à nouveau faire un `GET` et souhaite voir si le cache qu'il stocke est valid, il utilise le champ `If-Modified-Since` et la date qui lui a été donné à la dernière requête.
Le client contrôle si la date et antérieure ou non :
si le cache est valide, alors le serveur renvoie le code `403` sinon, il renvoie les données et à nouveau `Last-Modified'.

Dans le serveur, la valeur de `Last-Modified` est mise à jour à chaque fois qu'un `DELETE`, `PUT`, `POST` est réussi.
Un autre cas important, quand l'ICAO d'un avion dans le catalogue est mis à jour et que ce dernier est présent dans une flote actuelle, il est également mis à jour dans le JSON de la compangie. A ce moment, il faut également mettre à jour la date de modification.

### Endpoint Avion
Nous n'avons pas mis de système de cache sur cet endpoint car, au vu de son utilisation, il n'est pas utile.
Dans son utilisation normale, cet endpoint est utilisé par plusieurs clients afin d'y appliquer régulièrement plusieurs filtres et plusieurs règles de tri. 
Au vu du caractère très modulable, on part du principe que le cas où le même client envoie deux fois de suite la même requête n'est quasiment jamais vérifié.

## Auteurs

- [Quentin Michon](https://github.com/QuentinMichon)
- [Gianni Bee](https://github.com/GinByte)

Avec l’aide de GitHub Copilot et ChatGPT 5.2 pour la rédaction des en-têtes de fonctions.
