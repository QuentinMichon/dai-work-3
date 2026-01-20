# Aircraft-Company API

L'API permet de gérer une base de données d'avions et de flottes de compagnies aériennes. Une compagnie peut acheter des avions présents dans le catalogue.
Elle est basée sur le protocole HTTP et utilise des données au format JSON.
L'API est découpée en deux parties : `Aircraft` et `Company`.

La partie `Aircraft` permet de gérer un catalogue d'avions. Il est possible d'ajouter, modifier, récupérer les informations et supprimer (modèle CRUD) des avions présents dans cette liste.
Dans la partie `GET`, plusieurs filtres sont disponibles. Les opérations possibles sont les suivantes :
- [Créer un avion](#créer-un-avion)
- [Lire tous les avions](#lire-tous-les-avions)
- [Mettre à jour un avion](#mettre-à-jour-un-avion) par son ICAO (identifiant)
- [Supprimer un avion](#supprimer-un-avion) par son ICAO

La partie `Company` contient des informations générales sur la compagnie ainsi que sa flotte d'avions. Pour qu'un avion puisse être acheté, ce dernier doit être présent dans le catalogue.
Cette partie est aussi basée sur le modèle CRUD.

- [Créer une compagnie](#créer-une-compagnie)
- [Lire les compagnies](#lire-les-compagnies)
- [Acheter des avions](#acheter-des-avions)
- [Vendre des avions](#vendre-des-avions)
- [Supprimer une compagnie](#supprimer-une-compagnie)

## Endpoints

### Créer un avion

- `POST /avions`

Permet d'ajouter un avion dans le catalogue.

#### Requête

Le body de la requête doit contenir un objet JSON avec ces entrées :
- `constructor` : nom de l'entreprise qui a construit l'avion
- `ICAO` : identifiant unique de l'avion (ICAO = International Civil Aviation Organization)
- `range` : distance en km que l'avion peut parcourir
- `maxCapacity` : nombre maximum de passagers

Exemple de body :
```json
{
    "constructor": "Airbus",
    "ICAO": "A359",
    "range": 15700,
    "maxCapacity": 350 
}
```

Exemple de requête avec curl :
```bash
# Poster un nouvel avion dans le catalogue
curl -X POST "https://api.dai.swisspotter.ch/avions" \
     -H "Content-Type: application/json" \
     -d '{
            "constructor": "Airbus",
            "ICAO": "A359",
            "range": 15700,
            "maxCapacity": 350 
        }'
```

#### Réponse

Le body de la réponse retourne les propriétés de l'avion créé au format JSON.
En cas d'erreur, une explication au format texte est retournée.

Exemple de réponse :
```json
{
    "constructor": "Airbus",
    "ICAO": "A359",
    "range": 15700,
    "maxCapacity": 350 
}
```

#### Status codes

- `201` (Created) : l'avion a été ajouté au catalogue
- `400` (Bad Request) : le body de la requête est invalide
- `409` (Conflict) : l'ICAO du nouvel avion est déjà utilisé
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

### Lire tous les avions

- `GET /avions`

Récupère tous les avions du catalogue. Il est possible de filtrer avec `capacity`, `range` et `constructor`. Il est également possible de trier par `ICAO`, `constructor` et/ou `range` dans l'ordre croissant ou décroissant.

#### Requête

La requête peut être utilisée avec ces différents query parameters :
- `sort` : prend `icao`, `constructor` et/ou `range` pour trier les avions. Préfixer avec un `-` pour trier dans l'ordre inverse.
- `constructor` : filtre les avions par le nom du constructeur
- `range` : filtre les avions par leur capacité de vol. Un nombre positif filtre avec `>`, un nombre négatif filtre avec `<`.
- `capacity` : filtre les avions par leur capacité max de passagers. Même logique que `range` pour les nombres positifs et négatifs.

Exemples avec curl :
```bash
# Filtrer les avions qui ont une capacité max entre 100 et 500
curl -X GET "https://api.dai.swisspotter.ch/avions?capacity=100&capacity=-500"

# Filtrer les avions construits par Airbus et les trier par ICAO dans l'ordre alphabétique
curl -X GET "https://api.dai.swisspotter.ch/avions?sort=icao&constructor=Airbus"
```

#### Réponse

Le body de la réponse contient une liste des avions au format JSON avec les entrées suivantes :
- `constructor` : nom de l'entreprise qui a construit l'avion
- `ICAO` : identifiant unique de l'avion
- `range` : distance en km que l'avion peut parcourir
- `maxCapacity` : nombre maximum de passagers

Exemple :
```json
[
   {
     "constructor": "Airbus",
     "ICAO": "A318",
     "range": 5750,
     "maxCapacity": 132
   },
   {
     "constructor": "Airbus", 
     "ICAO": "A319N",
     "range": 6950,
     "maxCapacity": 156
   },
   {
     "constructor": "Airbus",
     "ICAO": "A388",
     "range": 15200,
     "maxCapacity": 525
   }
]
```

#### Status Codes

- `200` (OK)
- `400` (Bad Request) : les paramètres sont incorrects

### Mettre à jour un avion

- `PUT /avions`

Permet de mettre à jour les données d'un avion en l'identifiant par son ICAO.

#### Requête

La requête prend en query param `icao` de l'avion à mettre à jour.
Le body contient un objet JSON avec les données à mettre à jour :
- `constructor` : nom de l'entreprise qui a construit l'avion
- `ICAO` : identifiant unique de l'avion
- `range` : distance en km que l'avion peut parcourir
- `maxCapacity` : nombre maximum de passagers

Exemple avec curl :
```bash
# Mettre à jour la capacité max et l'ICAO
curl -X PUT "https://api.dai.swisspotter.ch/avions?icao=A359" \
     -H "Content-Type: application/json" \
     -d '{
            "ICAO": "A358",
            "maxCapacity": 325
        }'
```

#### Response

Retourne un JSON de l'avion après modification :
```json
{
  "constructor": "Airbus",
  "ICAO": "A358",
  "range": 15700,
  "maxCapacity": 325
}
```

#### Status Codes

- `200` (OK) : l'avion a été mis à jour
- `400` (Bad Request) : erreur dans les paramètres ou le body
- `404` (Not Found) : l'ICAO passé en paramètre n'existe pas
- `409` (Conflict) : le nouvel ICAO est déjà pris par un autre avion
- `424` (Failed Dependency) : l'ICAO a été modifié, mais n'a pas pu être mis à jour dans les flottes des compagnies
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

### Supprimer un avion

- `DELETE /avions`

Permet de supprimer un avion du catalogue en fournissant son ICAO.

#### Requête

La requête prend en query param `icao` : l'ICAO de l'avion à supprimer.

Exemple avec curl :
```bash
# Supprimer un avion
curl -X DELETE "https://api.dai.swisspotter.ch/avions?icao=A359"
```

#### Réponse

Retourne dans le body une liste JSON contenant l'avion supprimé, ou une liste vide si l'avion n'existait pas.

Exemple :
```json
[
  {
    "constructor": "Airbus",
    "ICAO": "A359",
    "range": 15700,
    "maxCapacity": 325
  }
]
```

#### Status Codes

- `200` (OK) : l'avion est supprimé
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

### Créer une compagnie

- `POST /company`

Permet de créer une nouvelle compagnie avec sa flotte. Le format JSON attendu contient les entrées suivantes :
- `companyICAO` : identifiant unique de la compagnie aérienne
- `name` : nom de la compagnie aérienne
- `country` : pays d'origine
- `fleet` : flotte d'avions (tableau de tuples) :
  - `aircraftICAO` : identifiant de l'avion
  - `quantity` : quantité en possession

#### Requête

La requête attend un body JSON avec les champs ci-dessus. La flotte peut être vide. Les autres champs sont obligatoires.

Exemple avec curl :
```bash
curl -X POST "https://api.dai.swisspotter.ch/company" \
     -H "Content-Type: application/json" \
     -d '{
            "companyICAO": "AFR",
            "name": "Air France",
            "country": "France",
            "fleet": [
              {
                "aircraftICAO": "A319",
                "quantity": 43
              },
              {
                "aircraftICAO": "A320",
                "quantity": 41
              }
            ]
        }'
```

#### Réponse

La réponse retourne la compagnie qui vient d'être créée au format JSON :
```json
{
  "companyICAO": "AFR",
  "name": "Air France",
  "country": "France",
  "fleet": [
    {
      "aircraftICAO": "A319",
      "quantity": 43
    },
    {
      "aircraftICAO": "A320",
      "quantity": 41
    }
  ]
}
```

#### Status Codes

- `201` (Created) : la compagnie a été ajoutée
- `400` (Bad Request) : le body est invalide
- `409` (Conflict) : l'ICAO fourni est déjà utilisé par une autre compagnie
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

### Lire les compagnies

- `GET /company`

Permet de lire les compagnies enregistrées dans la base de données ainsi que leur flotte. Plusieurs filtres et tris sont disponibles.

#### Requête

La requête peut être utilisée avec ces différents query parameters :
- `sort` : prend `name`, `companyICAO`, `fleetSize` ou `country` pour trier les compagnies. Préfixer par un `-` pour l'ordre décroissant.
- `country` : filtre par pays d'origine
- `fleetSize` : filtre par nombre d'avions dans la flotte. Préfixer par un `-` pour indiquer "moins que..."

Exemple avec curl :
```bash
# Trier les compagnies par nom, filtrer par taille de flotte entre 50 et 100 avions
curl -X GET "https://api.dai.swisspotter.ch/company?sort=name&fleetSize=50&fleetSize=-100"
```

#### Réponse

Retourne une liste de compagnies au format JSON avec les entrées présentées [ici](#créer-une-compagnie).

Exemple :
```json
[
    {
      "companyICAO": "AFR",
      "name": "Air France",
      "country": "France",
      "fleet": [
        {
          "aircraftICAO": "A319",
          "quantity": 43
        },
        {
          "aircraftICAO": "A320",
          "quantity": 41
        }
      ]
    }
]
```

#### Status Codes

- `200` (OK)
- `304` (Not Modified) : indique que le cache est valide côté client
- `400` (Bad Request) : erreur dans les paramètres de la requête

### Acheter des avions

- `PUT /company/{cmpICAO}/buy`

Permet d'ajouter des avions présents dans le catalogue à la flotte d'une compagnie aérienne. Il est possible de spécifier le nombre d'avions souhaité (par défaut : 1).
Si l'avion est déjà présent dans la flotte, le serveur incrémente la quantité. Sinon, il crée un nouveau tuple dans `fleet`.

#### Requête

La requête prend comme path parameter `cmpICAO` : l'ICAO de la compagnie.
Elle prend également comme query parameters :
- `aircraftICAO` : l'avion à ajouter
- `quantity` (optionnel) : la quantité souhaitée

Exemple avec curl :
```bash
# Ajouter 10 avions à la compagnie EZY
curl -X PUT "https://api.dai.swisspotter.ch/company/ezy/buy?aircraftICAO=B727&quantity=10"
```

#### Réponse

Retourne les informations complètes de la compagnie après incrémentation de la flotte :

```json
{
  "companyICAO": "EZY",
  "name": "easyJet",
  "country": "United Kingdom",
  "fleet": [
    {
      "aircraftICAO": "A319-100",
      "quantity": 61
    },
    {
      "aircraftICAO": "A318",
      "quantity": 83
    },
    {
      "aircraftICAO": "B748",
      "quantity": 68
    },
    {
      "aircraftICAO": "A388",
      "quantity": 20
    },
    {
      "aircraftICAO": "B727",
      "quantity": 10
    }
  ]
}
```

#### Status Codes

- `200` (OK) : les avions ont été ajoutés
- `400` (Bad Request) : la requête est invalide
- `404` (Not Found) : la compagnie n'existe pas ou l'avion ne se trouve pas dans le catalogue
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

### Vendre des avions

- `PUT /company/{cmpICAO}/sell`

Permet de retirer des avions de la flotte d'une compagnie aérienne. Il est possible de spécifier le nombre d'avions à vendre (par défaut : 1). La quantité ne doit pas dépasser le nombre d'avions de ce type possédés par la compagnie.

#### Requête

La requête prend comme path parameter `cmpICAO` : l'ICAO de la compagnie.
Elle prend également comme query parameters :
- `aircraftICAO` : l'avion à retirer
- `quantity` (optionnel) : la quantité souhaitée

Exemple avec curl :
```bash
# Retirer 10 avions de la compagnie EZY
curl -X PUT "https://api.dai.swisspotter.ch/company/ezy/sell?aircraftICAO=B727&quantity=10"
```

#### Réponse

Retourne les informations complètes de la compagnie après modification de la flotte :

```json
{
  "companyICAO": "EZY",
  "name": "easyJet",
  "country": "United Kingdom",
  "fleet": [
    {
      "aircraftICAO": "A319-100",
      "quantity": 61
    },
    {
      "aircraftICAO": "A318",
      "quantity": 83
    },
    {
      "aircraftICAO": "B748",
      "quantity": 68
    },
    {
      "aircraftICAO": "A388",
      "quantity": 20
    }
  ]
}
```

Ici, les 10 B727 ont été retirés. La compagnie en possédait exactement 10, donc le tuple B727 n'existe plus dans la flotte.

#### Status Codes

- `200` (OK) : les avions ont été retirés
- `400` (Bad Request) : la requête est invalide
- `404` (Not Found) : la compagnie n'existe pas ou l'avion ne se trouve pas dans la flotte
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

### Supprimer une compagnie

- `DELETE /company`

Permet de supprimer entièrement une compagnie de la base de données.

#### Requête

La requête prend comme query param :
- `companyICAO` : l'identifiant de la compagnie à supprimer

Exemple avec curl :
```bash
# Supprimer la compagnie easyJet (ICAO = EZY)
curl -X DELETE "https://api.dai.swisspotter.ch/company?companyICAO=ezy"
```

#### Réponse

Retourne l'état de la compagnie avant la suppression :

```json
{
  "companyICAO": "ezy",
  "name": "EasyJet",
  "country": "Switzerland",
  "fleet": [
    {
      "aircraftICAO" : "A320",
      "quantity" : 10
    }
  ]
}
```

#### Status Codes

- `200` (OK) : la compagnie a été supprimée
- `400` (Bad Request) : la requête n'est pas conforme
- `404` (Not Found) : l'ICAO fourni ne correspond à aucune compagnie connue
- `500` (Internal Server Error) : le fichier JSON sur le serveur n'a pas pu être mis à jour

---

>[!NOTE] 
>Si vous trouvez des erreurs, merci de créer une issue sur le [repo](https://github.com/QuentinMichon/dai-work-3/issues).

## Auteurs

- [Quentin Michon](https://github.com/QuentinMichon)
- [Gianni Bee](https://github.com/GinByte)

Avec l’aide de GitHub Copilot et ChatGPT 5.2 pour la rédaction des en-têtes de fonctions.