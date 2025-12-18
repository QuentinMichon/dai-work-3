# Traefik

Nous utilisons traefik comme reverse proxy dans notre infrastructure réseau. Il est important de run ce container 
avant de run celui de l'API (airAPI). 

## Variable d'environnement
Avant de run le container, il est important de setup le fichier `.env`. Pour ceci vous touverez un fichier `.env.example`.
Vous pouvez le renommer en `.env` avec cette commande

```bash
    # renommer le fichier d'environnement
    mv .env.example .env
```

Puis, vous devez remplir les 2 champs vides :
- `TRAEFIK_ACME_EMAIL` e-mail pour vous avertir quand il faut un nouveau certificat `let's encrypt`
- `TRAEFIK_FULLY_QUALIFIED_DOMAIN_NAME` le nom de domaine à utiliser pour accèder au service (doit être le même qui redirige l'address IP de la VM Microsoft Azure). Dans notre projet, il s'agit de `dai.swisspotter.ch`. Ce champ est laissé vide au cas où vous voulez héberger le service sur un autre serveur.

## Run Traefik

Il suffit de lancer le container avec docker compose avec cette commande 

```bash
    # il faut se trouver dans le même dossier que compose.yml
    docker compose up -d
```

## Issue

Si vous voyer des erreurs de lecture du fichier `letsencrypt/acme.json` c'est souvent un problème de droit d'écriture.
Run les commandes suivantes qui permettent d'effacer le fichier actuel et créer un nouveau puis configurer les bons droits d'accès avant de relancer le container.

```bash
  docker compose down
  sudo rm -rf letsencrypt
  sudo mkdir -p letsencrypt
  sudo touch letsencrypt/acme.json
  sudo chmod 600 letsencrypt/acme.json
  sudo chown -R ubuntu:ubuntu letsencrypt
  docker compose up -d
  docker compose logs -f traefik
```

Merci de nous faire une [issue](https://github.com/QuentinMichon/dai-work-3/issues) si vous trouvez d'autres problèmes. 