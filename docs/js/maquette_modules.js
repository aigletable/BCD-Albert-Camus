/* ============================================================================
 * BCD Express — Modules complémentaires de la maquette "Le Comptoir"
 * Écrans : 📚 Les Livres • 🎒 Mallettes • ⚙️ Gestion (Élèves, Étiquettes, Récolement, Outils)
 * Fonctionnement 100 % autonome en local (file://) sans dépendance réseau ni modules ES.
 * ============================================================================ */

(function () {
    'use strict';

    // ------------------------------------------------------------------------
    // 1. EXTENSION DES PRESETS SPATIAUX POUR COUVRIR TOUTES LES ZONES DU CATALOGUE
    // ------------------------------------------------------------------------
    if (typeof SHELF_PRESETS !== 'undefined') {
        if (!SHELF_PRESETS['6_haut']) {
            SHELF_PRESETS['6_haut'] = {
                name: "Îlot central 6 • Albums A–T",
                targetFace: "haut",
                faceName: "Face Avant (Côté Verrière)",
                tierName: "Rayon 2 (Albums illustrés)",
                desc: "Albums classés par ordre alphabétique d'auteur (A à T)",
                deskRelation: "👁️ Visible depuis le bureau en biais",
                deskBadgeClass: "success",
                targetLook: { x: 4.2, y: 0.85, z: 3.35 },
                camPos: { x: 4.2, y: 1.45, z: 1.6 },
                highlight: { w: 1.5, h: 0.045, d: 0.28, x: 4.2, y: 0.82, z: 3.35 }
            };
        }
        if (!SHELF_PRESETS['6_bas']) {
            SHELF_PRESETS['6_bas'] = {
                name: "Îlot central 6 • Revues & Magazines",
                targetFace: "bas",
                faceName: "Face Arrière (Côté Bacs CP)",
                tierName: "Bac & présentoir bas",
                desc: "Astrapi, J'aime Lire, Images Doc, Petit Léonard",
                deskRelation: "🔄 Face Sud de l'îlot 6 (vers les bacs CP)",
                deskBadgeClass: "late",
                targetLook: { x: 4.2, y: 0.75, z: 3.65 },
                camPos: { x: 4.2, y: 1.45, z: 5.2 },
                highlight: { w: 1.5, h: 0.045, d: 0.28, x: 4.2, y: 0.72, z: 3.65 }
            };
        }
        if (!SHELF_PRESETS['9']) {
            SHELF_PRESETS['9'] = {
                name: "Meuble 9 • Bacs bois fenêtres CP/CE1",
                targetFace: "gauche",
                faceName: "Bac bois sous radiateur",
                tierName: "Compartiments bas CP/CE1",
                desc: "Premières lectures et documentaires Wapiti CP/CE1",
                deskRelation: "🔄 Sous les grandes fenêtres (fond de salle)",
                deskBadgeClass: "late",
                targetLook: { x: 4.95, y: 0.45, z: 7.05 },
                camPos: { x: 4.95, y: 1.65, z: 5.4 },
                highlight: { w: 1.0, h: 0.14, d: 0.55, x: 4.95, y: 0.58, z: 7.05 }
            };
        }
    }

    // ------------------------------------------------------------------------
    // 2. DONNÉES PARTAGÉES DE LA BCD ALBERT CAMUS
    // ------------------------------------------------------------------------
    const CATALOG_BOOKS = [
        {
            id: 'bk-1',
            titre: 'Charlie et la Chocolaterie',
            auteur: 'Roald Dahl',
            editeur: 'Gallimard Jeunesse',
            isbn: '9782070612802',
            cote: 'R DAH',
            genre: 'Roman',
            theme: 'Humour & Fantaisie',
            niveau: 'Cycle 3 (CM1-CM2)',
            emoji: '🍫',
            gradient: 'linear-gradient(135deg, #D49B28, #F59E0B)',
            zoneKey: '1_droite',
            zoneLabel: 'Étagère 1 • Face Droite (Rayon 2)',
            malletteCode: null,
            addedOrder: 12,
            ressources: [
                'Fiche de compréhension CM1 (chapitres 1 à 10)',
                'Questionnaire rallye lecture niveau 2'
            ],
            exemplaires: [
                { id: '#101', statut: 'DISPONIBLE', dateAjout: '12/09/2025', etat: 'Bon état' },
                { id: '#102', statut: 'EMPRUNTE', emprunteur: 'Lucas DUPONT (CM1-A)', dateAjout: '12/09/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-2',
            titre: "Harry Potter à l'école des sorciers",
            auteur: 'J.K. Rowling',
            editeur: 'Gallimard Jeunesse',
            isbn: '9782070584628',
            cote: 'R ROW',
            genre: 'Roman',
            theme: 'Magie & Séries',
            niveau: 'Cycle 3 (CM1-CM2)',
            emoji: '⚡',
            gradient: 'linear-gradient(135deg, #2A5C8A, #1E3A8A)',
            zoneKey: '3',
            zoneLabel: 'Mur 3 • Séries (Rayon 1 Haut)',
            malletteCode: null,
            addedOrder: 11,
            ressources: [
                'Livret vocabulaire fantastique',
                'Affiche blasons Poudlard (atelier arts)'
            ],
            exemplaires: [
                { id: '#108', statut: 'DISPONIBLE', dateAjout: '04/09/2025', etat: 'Neuf' },
                { id: '#109', statut: 'DISPONIBLE', dateAjout: '04/09/2025', etat: 'Bon état' },
                { id: '#110', statut: 'EMPRUNTE', emprunteur: 'Léa STOCKISTE (CM2-B)', dateAjout: '04/09/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-3',
            titre: 'Le Petit Prince',
            auteur: 'Antoine de Saint-Exupéry',
            editeur: 'Folio Junior',
            isbn: '9782070612758',
            cote: 'R SAI',
            genre: 'Conte philosophique',
            theme: 'Classiques & Amitié',
            niveau: 'Cycle 3 (CM1-CM2)',
            emoji: '👑',
            gradient: 'linear-gradient(135deg, #1E5139, #388E67)',
            zoneKey: '1_droite',
            zoneLabel: 'Étagère 1 • Face Droite (Rayon 2)',
            malletteCode: 'MAL-002',
            addedOrder: 10,
            ressources: [
                'Séquence EMC : amitié et responsabilité',
                'Lecture offerte audio (classe)'
            ],
            exemplaires: [
                { id: '#114', statut: 'EMPRUNTE', emprunteur: 'Lucas DUPONT (CM1-A)', dateAjout: '10/09/2025', etat: 'Bon état' },
                { id: '#115', statut: 'DISPONIBLE', dateAjout: '10/09/2025', etat: 'À couvrir' }
            ]
        },
        {
            id: 'bk-4',
            titre: 'Contes de Perrault',
            auteur: 'Charles Perrault',
            editeur: 'Lito',
            isbn: '9782244412306',
            cote: 'C PER',
            genre: 'Contes',
            theme: 'Contes traditionnels',
            niveau: 'Cycle 2 (CE1-CE2)',
            emoji: '🧚',
            gradient: 'linear-gradient(135deg, #7C3AED, #A855F7)',
            zoneKey: '1_gauche',
            zoneLabel: 'Étagère 1 • Face Gauche (Côté Bureau)',
            malletteCode: 'MAL-002',
            addedOrder: 9,
            ressources: [
                'Tableau comparatif Petit Chaperon / Cendrillon',
                'Cartes personnages pour théâtre de marionnettes'
            ],
            exemplaires: [
                { id: '#121', statut: 'DISPONIBLE', dateAjout: '15/09/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-5',
            titre: 'Astérix le Gaulois',
            auteur: 'René Goscinny & Uderzo',
            editeur: 'Hachette / Albert René',
            isbn: '9782012101333',
            cote: 'BD GOS',
            genre: 'BD',
            theme: 'Histoire & Humour',
            niveau: 'Cycle 2 & 3',
            emoji: '💥',
            gradient: 'linear-gradient(135deg, #DC2626, #F97316)',
            zoneKey: '2',
            zoneLabel: 'Bac BD 2 • Niveau 1 (Supérieur)',
            malletteCode: null,
            addedOrder: 8,
            ressources: [
                'Dossier Gaule romaine CE2',
                'Atelier bulles et onomatopées'
            ],
            exemplaires: [
                { id: '#130', statut: 'DISPONIBLE', dateAjout: '02/10/2025', etat: 'Bon état' },
                { id: '#131', statut: 'DISPONIBLE', dateAjout: '02/10/2025', etat: 'Coin usé' }
            ]
        },
        {
            id: 'bk-6',
            titre: "L'Encyclopédie des Dinosaures",
            auteur: 'Collectif Fleurus',
            editeur: 'Fleurus',
            isbn: '9782215146520',
            cote: '567 DIN',
            genre: 'Documentaire',
            theme: 'Sciences & Préhistoire',
            niveau: 'Cycle 2 (CP-CE2)',
            emoji: '🦖',
            gradient: 'linear-gradient(135deg, #15803D, #10B981)',
            zoneKey: '8_droite',
            zoneLabel: 'Biblio Verte 8 • Face Droite (Côté Tapis)',
            malletteCode: 'MAL-003',
            addedOrder: 7,
            ressources: [
                'Frise géologique simplifiée CP/CE1',
                'Fiches exposition "Herbivores vs Carnivores"'
            ],
            exemplaires: [
                { id: '#142', statut: 'DISPONIBLE', dateAjout: '18/09/2025', etat: 'Neuf' }
            ]
        },
        {
            id: 'bk-7',
            titre: 'Cornebidouille',
            auteur: 'Pierre Bertrand & Magali Bonniol',
            editeur: "L'école des loisirs",
            isbn: '9782211073882',
            cote: 'A BER',
            genre: 'Album',
            theme: 'Sorcières & Humour',
            niveau: 'Cycle 2 (CP-CE1)',
            emoji: '🧙‍♀️',
            gradient: 'linear-gradient(135deg, #6D28D9, #EC4899)',
            zoneKey: '8_gauche',
            zoneLabel: 'Biblio Verte 8 • Face Gauche (Côté Bureau)',
            malletteCode: null,
            addedOrder: 6,
            ressources: [
                'Recette de soupe de sorcière (production d’écrits CP)'
            ],
            exemplaires: [
                { id: '#150', statut: 'DISPONIBLE', dateAjout: '22/09/2025', etat: 'Bon état' },
                { id: '#151', statut: 'EMPRUNTE', emprunteur: 'Camille MARTIN (CE2)', dateAjout: '22/09/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-8',
            titre: 'Max et les Maximonstres',
            auteur: 'Maurice Sendak',
            editeur: "L'école des loisirs",
            isbn: '9782211011341',
            cote: 'A SEN',
            genre: 'Album',
            theme: 'Émotions & Imaginaire',
            niveau: 'Cycle 2 (CP-CE1)',
            emoji: '👹',
            gradient: 'linear-gradient(135deg, #B45309, #D97706)',
            zoneKey: '6_haut',
            zoneLabel: 'Îlot 6 • Face Avant (Albums A–T)',
            malletteCode: 'MAL-004',
            addedOrder: 5,
            ressources: [
                'Débat philo : gérer la colère (CP)'
            ],
            exemplaires: [
                { id: '#158', statut: 'EMPRUNTE', emprunteur: 'Classe CP (Mme Roy)', dateAjout: '08/10/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-9',
            titre: 'Astrapi — Le Mystère des Océans',
            auteur: 'Bayard Jeunesse',
            editeur: 'Bayard',
            isbn: '9791036345128',
            cote: 'REV AST',
            genre: 'Revue',
            theme: 'Sciences & Actualités',
            niveau: 'Cycle 2 & 3',
            emoji: '📰',
            gradient: 'linear-gradient(135deg, #0284C7, #06B6D4)',
            zoneKey: '6_bas',
            zoneLabel: 'Îlot 6 • Face Arrière (Revues)',
            malletteCode: null,
            addedOrder: 4,
            ressources: [
                'Mini-quizz lecture rapide 10 minutes'
            ],
            exemplaires: [
                { id: '#163', statut: 'DISPONIBLE', dateAjout: '02/11/2025', etat: 'Neuf' }
            ]
        },
        {
            id: 'bk-10',
            titre: 'Max et Lili ont peur des autres',
            auteur: 'Dominique de Saint Mars',
            editeur: 'Calligram',
            isbn: '9782884805018',
            cote: 'R SAI',
            genre: 'Roman illustré',
            theme: 'Vivre ensemble & Harcèlement',
            niveau: 'Cycle 2 & 3',
            emoji: '🤝',
            gradient: 'linear-gradient(135deg, #0F766E, #14B8A6)',
            zoneKey: '1_droite',
            zoneLabel: 'Étagère 1 • Face Droite (Rayon 2)',
            malletteCode: 'MAL-001',
            addedOrder: 3,
            ressources: [
                'Guide séance EMC : harcèlement et empathie',
                'Questions de fin de livre pour le conseil de classe'
            ],
            exemplaires: [
                { id: '#171', statut: 'EMPRUNTE', emprunteur: 'CM1-A (Mallette MAL-001)', dateAjout: '19/09/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-11',
            titre: 'Wapiti — Les Dinosaures géants',
            auteur: 'Milan Presse',
            editeur: 'Milan',
            isbn: '9782408023454',
            cote: '567 WAP',
            genre: 'Documentaire',
            theme: 'Sciences & Préhistoire',
            niveau: 'Cycle 2 (CP-CE1)',
            emoji: '🦕',
            gradient: 'linear-gradient(135deg, #65A30D, #84CC16)',
            zoneKey: '9',
            zoneLabel: 'Bacs bois 9 • Fenêtres CP/CE1',
            malletteCode: 'MAL-003',
            addedOrder: 2,
            ressources: [
                'Fiches de lecture autonome CP'
            ],
            exemplaires: [
                { id: '#176', statut: 'DISPONIBLE', dateAjout: '14/10/2025', etat: 'Bon état' }
            ]
        },
        {
            id: 'bk-12',
            titre: 'La Couleur des Émotions',
            auteur: 'Anna Llenas',
            editeur: 'Quatre Fleuves',
            isbn: '9782841969715',
            cote: 'A LLE',
            genre: 'Album',
            theme: 'Vivre ensemble & Harcèlement',
            niveau: 'Cycle 2 (CP-CE1)',
            emoji: '🎨',
            gradient: 'linear-gradient(135deg, #C85434, #E2795D)',
            zoneKey: '6_haut',
            zoneLabel: 'Îlot 6 • Face Avant (Albums A–T)',
            malletteCode: 'MAL-001',
            addedOrder: 1,
            ressources: [
                'Atelier flacons des émotions (Cycle 2)'
            ],
            exemplaires: [] // Volontairement 0 exemplaire pour tester l'anomalie & la Boîte à vérifier
        }
    ];

    const MALLETTES_DATA = [
        {
            id: 'MAL-001',
            code: 'MAL-001',
            titre: 'Vivre ensemble & Anti-harcèlement',
            theme: 'Vivre ensemble & EMC',
            niveau: 'Cycle 2 & 3',
            statut: 'EN_PRET',
            emprunteur: 'Mme Dupont — Classe CM1-A',
            dateRetour: '12 décembre 2026',
            accentColor: '#1E5139',
            description: "Sélection d'albums et petits romans pour mener des ateliers de parole, prévenir le harcèlement scolaire et travailler l'empathie en classe.",
            items: [
                { bookId: 'bk-10', exId: '#171', qty: 1 },
                { bookId: 'bk-12', exId: '#--', qty: 1 }
            ],
            ressources: [
                { titre: 'Séquence EMC 4 séances (CM1-CM2)', type: 'PDF Pédagogique' },
                { titre: 'Grille de jeu de rôle "Message clair"', type: 'Fiche classe' }
            ],
            historique: [
                { date: '18/11/2026', action: 'Prêtée à Mme Dupont (CM1-A)', par: 'Bénévole BCD' },
                { date: '15/10/2026', action: 'Retournée complète par M. Martin (CE2)', par: 'Admin BCD' }
            ]
        },
        {
            id: 'MAL-002',
            code: 'MAL-002',
            titre: 'Contes traditionnels & Merveilleux',
            theme: 'Contes & Poésie',
            niveau: 'Cycle 2 (CE1-CE2)',
            statut: 'DISPONIBLE',
            emprunteur: null,
            dateRetour: null,
            accentColor: '#D49B28',
            description: "Ouvrages patrimoniaux de Perrault, Grimm et récits détournés pour travailler la structure du conte et le théâtre en classe.",
            items: [
                { bookId: 'bk-4', exId: '#121', qty: 1 },
                { bookId: 'bk-3', exId: '#115', qty: 1 }
            ],
            ressources: [
                { titre: 'Schéma narratif illustré (situation initiale ➔ morale)', type: 'Affichage A3' },
                { titre: 'Cartes objets magiques', type: 'Atelier écriture' }
            ],
            historique: [
                { date: '05/11/2026', action: 'Retournée par Mme Blanc (CE1)', par: 'Admin BCD' }
            ]
        },
        {
            id: 'MAL-003',
            code: 'MAL-003',
            titre: 'Sur les traces des Dinosaures',
            theme: 'Sciences & Préhistoire',
            niveau: 'Cycle 2 (CP-CE2)',
            statut: 'DISPONIBLE',
            emprunteur: null,
            dateRetour: null,
            accentColor: '#2A5C8A',
            description: "Documentaires illustrés et fiches d'observation issus de la Bibliothèque Verte 8 pour préparer une exposition scientifique de classe.",
            items: [
                { bookId: 'bk-6', exId: '#142', qty: 1 },
                { bookId: 'bk-11', exId: '#176', qty: 1 }
            ],
            ressources: [
                { titre: 'Quiz vrai/faux Jurassique', type: 'Fiche élève' },
                { titre: 'Modèles silhouettes à découper', type: 'Arts plastiques' }
            ],
            historique: [
                { date: '20/10/2026', action: 'Création de la mallette', par: 'Service Civique' }
            ]
        },
        {
            id: 'MAL-004',
            code: 'MAL-004',
            titre: 'Premières lectures & Émotions CP',
            theme: 'Premières lectures',
            niveau: 'CP',
            statut: 'EN_PRET',
            emprunteur: 'Mme Roy — Classe CP',
            dateRetour: '04 décembre 2026',
            accentColor: '#C85434',
            description: "Petits albums faciles à lire pour le coin regroupement de la classe de CP.",
            items: [
                { bookId: 'bk-8', exId: '#158', qty: 1 }
            ],
            ressources: [
                { titre: 'Boîte à mots illustrée CP', type: 'Outil lecture' }
            ],
            historique: [
                { date: '10/11/2026', action: 'Prêtée à Mme Roy (CP)', par: 'Admin BCD' }
            ]
        }
    ];

    const USERS_DATA = [
        {
            id: 'lucas_cm1',
            numero: 'EL-042',
            nom: 'DUPONT',
            prenom: 'Lucas',
            classe: 'CM1-A',
            type: 'ELEVE',
            avatar: '👦',
            quotaMax: 3,
            dernierPassage: 'Il y a 6 jours',
            prets: [
                { exId: '#114', titre: 'Le Petit Prince', auteur: 'Antoine de Saint-Exupéry', cote: 'R SAI', echeance: '30 novembre', retard: false, zoneKey: '1_droite', emoji: '👑', gradient: 'linear-gradient(135deg, #1E5139, #388E67)' },
                { exId: '#102', titre: 'Le Tour du Monde en 80 Jours', auteur: 'Jules Verne', cote: 'R VER', echeance: 'En retard de 4 jours', retard: true, zoneKey: '1_droite', emoji: '🌍', gradient: 'linear-gradient(135deg, #C85434, #E2795D)' }
            ]
        },
        {
            id: 'lea_cm2',
            numero: 'EL-087',
            nom: 'STOCKISTE',
            prenom: 'Léa',
            classe: 'CM2-B',
            type: 'ELEVE',
            avatar: '👧',
            quotaMax: 3,
            dernierPassage: 'Avant-hier',
            prets: [
                { exId: '#110', titre: "Harry Potter à l'école des sorciers", auteur: 'J.K. Rowling', cote: 'R ROW', echeance: '05 décembre', retard: false, zoneKey: '3', emoji: '⚡', gradient: 'linear-gradient(135deg, #2A5C8A, #1E3A8A)' }
            ]
        },
        {
            id: 'thomas_cp',
            numero: 'EL-009',
            nom: 'PETIT',
            prenom: 'Thomas',
            classe: 'CP',
            type: 'ELEVE',
            avatar: '🚼',
            quotaMax: 1,
            dernierPassage: 'Première visite mardi',
            prets: []
        },
        {
            id: 'hugo_ce1',
            numero: 'EL-018',
            nom: 'MOREL',
            prenom: 'Hugo',
            classe: 'CE1',
            type: 'ELEVE',
            avatar: '🧒',
            quotaMax: 2,
            dernierPassage: 'Il y a 4 jours',
            prets: [
                { exId: '#142', titre: "L'Encyclopédie des Dinosaures", auteur: 'Collectif Fleurus', cote: '567 DIN', echeance: '02 décembre', retard: false, zoneKey: '8_droite', emoji: '🦖', gradient: 'linear-gradient(135deg, #15803D, #10B981)' }
            ]
        },
        {
            id: 'camille_ce2',
            numero: 'EL-031',
            nom: 'MARTIN',
            prenom: 'Camille',
            classe: 'CE2',
            type: 'ELEVE',
            avatar: '👧',
            quotaMax: 3,
            dernierPassage: 'Hier',
            prets: [
                { exId: '#151', titre: 'Cornebidouille', auteur: 'Pierre Bertrand', cote: 'A BER', echeance: '07 décembre', retard: false, zoneKey: '8_gauche', emoji: '🧙‍♀️', gradient: 'linear-gradient(135deg, #6D28D9, #EC4899)' }
            ]
        },
        {
            id: 'ines_cm1',
            numero: 'EL-055',
            nom: 'BENALI',
            prenom: 'Inès',
            classe: 'CM1-A',
            type: 'ELEVE',
            avatar: '👧',
            quotaMax: 3,
            dernierPassage: 'Il y a 8 jours',
            prets: []
        },
        {
            id: 'noah_ce2',
            numero: 'EL-036',
            nom: 'ROUSSEAU',
            prenom: 'Noah',
            classe: 'CE2',
            type: 'ELEVE',
            avatar: '👦',
            quotaMax: 3,
            dernierPassage: 'Il y a 10 jours',
            prets: [
                { exId: '#130', titre: 'Astérix le Gaulois', auteur: 'Goscinny & Uderzo', cote: 'BD GOS', echeance: 'En retard de 2 jours', retard: true, zoneKey: '2', emoji: '💥', gradient: 'linear-gradient(135deg, #DC2626, #F97316)' }
            ]
        },
        {
            id: 'chloe_cp',
            numero: 'EL-012',
            nom: 'LEROY',
            prenom: 'Chloé',
            classe: 'CP',
            type: 'ELEVE',
            avatar: '👧',
            quotaMax: 1,
            dernierPassage: 'Mardi matin',
            prets: [
                { exId: '#176', titre: 'Wapiti — Les Dinosaures géants', auteur: 'Milan Presse', cote: '567 WAP', echeance: '03 décembre', retard: false, zoneKey: '9', emoji: '🦕', gradient: 'linear-gradient(135deg, #65A30D, #84CC16)' }
            ]
        },
        {
            id: 'leo_cp',
            numero: 'EL-014',
            nom: 'GARNIER',
            prenom: 'Léo',
            classe: 'CP',
            type: 'ELEVE',
            avatar: '👦',
            quotaMax: 1,
            dernierPassage: 'Il y a 10 jours',
            prets: []
        },
        {
            id: 'jade_ce1',
            numero: 'EL-022',
            nom: 'FOURNIER',
            prenom: 'Jade',
            classe: 'CE1',
            type: 'ELEVE',
            avatar: '👧',
            quotaMax: 2,
            dernierPassage: 'Hier',
            prets: []
        },
        {
            id: 'arthur_ce2',
            numero: 'EL-039',
            nom: 'GIRARD',
            prenom: 'Arthur',
            classe: 'CE2',
            type: 'ELEVE',
            avatar: '👦',
            quotaMax: 3,
            dernierPassage: 'Semaine dernière',
            prets: []
        },
        {
            id: 'gabriel_cm1',
            numero: 'EL-058',
            nom: 'LAURENT',
            prenom: 'Gabriel',
            classe: 'CM1-A',
            type: 'ELEVE',
            avatar: '👦',
            quotaMax: 3,
            dernierPassage: 'Il y a 3 jours',
            prets: [
                { exId: '#109', titre: "Harry Potter à l'école des sorciers", auteur: 'J.K. Rowling', cote: 'R ROW', echeance: '06 décembre', retard: false, zoneKey: '3', emoji: '⚡', gradient: 'linear-gradient(135deg, #2A5C8A, #1E3A8A)' }
            ]
        },
        {
            id: 'mael_cm2',
            numero: 'EL-091',
            nom: 'CHEVALIER',
            prenom: 'Maël',
            classe: 'CM2-B',
            type: 'ELEVE',
            avatar: '👦',
            quotaMax: 3,
            dernierPassage: 'Il y a 5 jours',
            prets: []
        },
        {
            id: 'ens_roy',
            numero: 'ENS-01',
            nom: 'ROY',
            prenom: 'Claire (Mme)',
            classe: 'CP',
            type: 'ENSEIGNANT',
            avatar: '👩‍🏫',
            quotaMax: 10,
            dernierPassage: 'Cette semaine',
            prets: [
                { exId: '#158', titre: 'Max et les Maximonstres', auteur: 'Maurice Sendak', cote: 'A SEN', echeance: '04 décembre', retard: false, zoneKey: '6_haut', emoji: '👹', gradient: 'linear-gradient(135deg, #B45309, #D97706)' }
            ]
        },
        {
            id: 'ens_blanc',
            numero: 'ENS-02',
            nom: 'BLANC',
            prenom: 'Julie (Mme)',
            classe: 'CE1',
            type: 'ENSEIGNANT',
            avatar: '👩‍🏫',
            quotaMax: 10,
            dernierPassage: 'Cette semaine',
            prets: []
        },
        {
            id: 'ens_martin',
            numero: 'ENS-03',
            nom: 'MARTIN',
            prenom: 'Paul (M.)',
            classe: 'CE2',
            type: 'ENSEIGNANT',
            avatar: '👨‍🏫',
            quotaMax: 10,
            dernierPassage: 'Hier',
            prets: []
        },
        {
            id: 'ens_dupont',
            numero: 'ENS-04',
            nom: 'DUPONT',
            prenom: 'Sophie (Mme)',
            classe: 'CM1-A',
            type: 'ENSEIGNANT',
            avatar: '👩‍🏫',
            quotaMax: 10,
            dernierPassage: 'Aujourd’hui',
            prets: [
                { exId: '#171', titre: 'Max et Lili ont peur des autres', auteur: 'D. de Saint Mars', cote: 'R SAI', echeance: '12 décembre', retard: false, zoneKey: '1_droite', emoji: '🤝', gradient: 'linear-gradient(135deg, #0F766E, #14B8A6)' }
            ]
        },
        {
            id: 'ens_simon',
            numero: 'ENS-05',
            nom: 'SIMON',
            prenom: 'Marc (M.)',
            classe: 'CM2-B',
            type: 'ENSEIGNANT',
            avatar: '👨‍🏫',
            quotaMax: 10,
            dernierPassage: 'Il y a 3 jours',
            prets: []
        }
    ];

    const PROFILES_DATA = [
        { id: 'prof_cp', role: 'TEACHER', classe: 'CP', teacherName: 'Mme Roy', badgeCode: 'ENS-01', emoji: '🌱', bg: 'linear-gradient(135deg, #10B981, #059669)' },
        { id: 'prof_ce1', role: 'TEACHER', classe: 'CE1', teacherName: 'Mme Blanc', badgeCode: 'ENS-02', emoji: '🦊', bg: 'linear-gradient(135deg, #F59E0B, #D97706)' },
        { id: 'prof_ce2', role: 'TEACHER', classe: 'CE2', teacherName: 'M. Martin', badgeCode: 'ENS-03', emoji: '🦉', bg: 'linear-gradient(135deg, #3B82F6, #1D4ED8)' },
        { id: 'prof_cm1', role: 'TEACHER', classe: 'CM1-A', teacherName: 'Mme Dupont', badgeCode: 'ENS-04', emoji: '🦁', bg: 'linear-gradient(135deg, #1E5139, #15803D)' },
        { id: 'prof_cm2', role: 'TEACHER', classe: 'CM2-B', teacherName: 'M. Simon', badgeCode: 'ENS-05', emoji: '🚀', bg: 'linear-gradient(135deg, #7C3AED, #5B21B6)' },
        { id: 'admin_bcd', role: 'ADMIN', classe: null, teacherName: 'Admin / Bénévole BCD', badgeCode: 'ADMIN', emoji: '🛠️', bg: 'linear-gradient(135deg, #334155, #0F172A)', pin: '1234' }
    ];

    const SESSION_STATE = {
        profileId: 'prof_cm1',
        role: 'TEACHER',
        classe: 'CM1-A',
        pinPromptOpen: false,
        pinError: ''
    };

    const LABEL_FORMATS = {
        AVERY_24: { name: 'Avery L7160 / Standard (24 étiquettes par page — 3 × 8)', perPage: 24, cols: 3, desc: '63,5 × 38,1 mm • Format idéal dos et couverture BCD' },
        AVERY_21: { name: 'Avery L7159 / Agipa (21 étiquettes par page — 3 × 7)', perPage: 21, cols: 3, desc: '63,5 × 38,1 mm avec marges larges' },
        AGIPA_40: { name: 'Mini codes-barres dos (40 étiquettes par page — 4 × 10)', perPage: 40, cols: 4, desc: '48,5 × 25,4 mm • Spécial tranches fines CP/albums' }
    };

    const BACKUPS_DATA = [
        { file: 'bcd_camus_2026-11-26_10h30.db', date: 'Aujourd’hui à 10h30', size: '1,42 Mo', type: 'Sauvegarde manuelle' },
        { file: 'bcd_camus_2026-11-25_17h45.db', date: 'Hier à 17h45', size: '1,41 Mo', type: 'Fermeture automatique' },
        { file: 'bcd_camus_2026-11-20_16h15.db', date: '20 novembre à 16h15', size: '1,38 Mo', type: 'Export avant vacances' }
    ];

    // ------------------------------------------------------------------------
    // 3. ÉTATS DES VUES
    // ------------------------------------------------------------------------
    let activeMainView = 'comptoir';

    const LIVRES_STATE = {
        viewMode: 'grid', // 'grid' | 'list'
        search: '',
        genre: 'ALL',
        theme: 'ALL',
        niveau: 'ALL',
        onlyZero: false,
        selectedBookId: 'bk-1'
    };

    const MALLETTES_STATE = {
        search: '',
        statut: 'ALL',
        theme: 'ALL',
        selectedId: 'MAL-001',
        subTab: 'contenu', // 'contenu' | 'ressources' | 'historique' | 'infos'
        booksView: 'grid', // 'grid' | 'table'
        selectedBookId: null
    };

    const GESTION_STATE = {
        subTab: 'eleves', // 'eleves' | 'etiquettes' | 'recolement' | 'outils'
        userSearch: '',
        userClasse: 'ALL',
        selectedUserId: 'lucas_cm1'
    };

    const ETIQUETTES_STATE = {
        filterText: '',
        onlyChecked: false,
        format: 'AVERY_24',
        startOffset: 1,
        headerText: 'ÉCOLE ALBERT CAMUS PASTEUR',
        showTitle: true,
        showBarcode: true,
        showBorders: false,
        focusedBookId: 'bk-1',
        selected: {
            'bk-1': { checked: true, qty: 2 },
            'bk-2': { checked: true, qty: 3 },
            'bk-6': { checked: true, qty: 1 }
        }
    };

    const RECOLEMENT_STATE = {
        sessionActive: false,
        perimetre: 'Tous les rayonnages (1 à 11)',
        filterStatus: 'ALL', // 'ALL' | 'MANQUANT' | 'PRESENT' | 'EN_PRET'
        search: '',
        selectedExId: null,
        items: []
    };

    function rebuildRecolementItems() {
        const list = [];
        CATALOG_BOOKS.forEach(book => {
            book.exemplaires.forEach(ex => {
                let statut = ex.statut === 'EMPRUNTE' ? 'EN_PRET' : 'MANQUANT';
                let info = ex.statut === 'EMPRUNTE' ? ('Emprunté : ' + (ex.emprunteur || 'Élève')) : 'Non encore bippé en rayon';
                let motif = '';
                // Pré-pointer 2 exemplaires déjà vérifiés pour rendre la démo parlante au lancement
                if (ex.id === '#101' || ex.id === '#108') {
                    statut = 'PRESENT';
                    info = 'Bippé en rayon à 09h18';
                }
                list.push({
                    exId: ex.id,
                    bookId: book.id,
                    titre: book.titre,
                    auteur: book.auteur,
                    isbn: book.isbn,
                    zoneLabel: book.zoneLabel,
                    statut,
                    motif,
                    info,
                    remarque: ex.etat || 'Bon état'
                });
            });
        });
        RECOLEMENT_STATE.items = list;
    }

    // ------------------------------------------------------------------------
    // 4. HELPERS GÉNÉRAUX, MODALE & NAVIGATION
    // ------------------------------------------------------------------------
    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function notify(msg, icon) {
        if (typeof window.showToast === 'function') {
            window.showToast(msg, icon || '✨');
        }
    }

    function chime() {
        if (typeof window.playSound === 'function') {
            window.playSound('success');
        }
    }

    function openModal(title, bodyHtml, footerHtml, extraCardClass) {
        const overlay = document.getElementById('appModalOverlay');
        const card = document.getElementById('appModalCard');
        if (!overlay || !card) return;
        card.className = extraCardClass ? `modal-card ${extraCardClass}` : 'modal-card';
        card.innerHTML = `
            <div class="modal-header">
                <h3>${title}</h3>
                <button type="button" class="modal-close-btn" onclick="closeModal()" aria-label="Fermer">✕</button>
            </div>
            <div class="modal-body">${bodyHtml}</div>
            ${footerHtml ? `<div class="modal-footer">${footerHtml}</div>` : ''}
        `;
        overlay.classList.add('open');
    }

    function closeModal() {
        const overlay = document.getElementById('appModalOverlay');
        if (overlay) overlay.classList.remove('open');
    }

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') closeModal();
    });

    function switchMainView(viewId) {
        activeMainView = viewId;
        const mapIds = {
            comptoir: 'view-comptoir',
            livres: 'view-livres',
            mallettes: 'view-mallettes',
            gestion: 'view-gestion'
        };

        document.querySelectorAll('.app-view').forEach(el => el.classList.remove('active'));
        const target = document.getElementById(mapIds[viewId]);
        if (target) target.classList.add('active');

        document.querySelectorAll('.main-nav .nav-link').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.view === viewId);
        });

        const dock = document.getElementById('comptoirDockBar');
        if (dock) {
            dock.style.display = viewId === 'comptoir' ? 'flex' : 'none';
        }

        if (viewId === 'livres') renderLivresView();
        else if (viewId === 'mallettes') renderMallettesView();
        else if (viewId === 'gestion') renderGestionView();
        else if (viewId === 'comptoir') {
            setTimeout(() => window.dispatchEvent(new Event('resize')), 40);
        }
    }

    // ------------------------------------------------------------------------
    // 5. PASSERELLES CROSS-VUES VERS LE COMPTOIR & ÉTIQUETTES
    // ------------------------------------------------------------------------
    function locateBookOnComptoir(bookId) {
        const book = CATALOG_BOOKS.find(b => b.id === bookId);
        if (!book) return;
        switchMainView('comptoir');
        chime();
        setTimeout(() => {
            if (typeof window.highlightBcdZone === 'function') {
                window.highlightBcdZone(book.zoneKey, true);
            }
            notify(`<strong>${escapeHtml(book.titre)}</strong> ➔ ${escapeHtml(book.zoneLabel)}`, '🗺️');
        }, 60);
    }

    function sendBookToEtiquettes(bookId) {
        const book = CATALOG_BOOKS.find(b => b.id === bookId);
        if (!book) return;
        const qty = Math.max(1, book.exemplaires.length || 1);
        ETIQUETTES_STATE.selected[book.id] = { checked: true, qty };
        ETIQUETTES_STATE.focusedBookId = book.id;
        GESTION_STATE.subTab = 'etiquettes';
        switchMainView('gestion');
        chime();
        notify(`<strong>${escapeHtml(book.titre)}</strong> ajouté aux étiquettes (${qty})`, '🏷️');
    }

    // ------------------------------------------------------------------------
    // 5B. ÉCRAN D'ACCUEIL NETFLIX & SÉLECTEUR DE PROFIL / CLASSE
    // ------------------------------------------------------------------------
    function getActiveProfile() {
        return PROFILES_DATA.find(p => p.id === SESSION_STATE.profileId) || PROFILES_DATA[3];
    }

    function renderProfileSplash() {
        const overlay = document.getElementById('profileSplashOverlay');
        if (!overlay) return;

        const tilesHtml = PROFILES_DATA.map(p => {
            const isAdmin = p.role === 'ADMIN';
            const students = isAdmin
                ? USERS_DATA.filter(u => u.type === 'ELEVE')
                : USERS_DATA.filter(u => u.type === 'ELEVE' && u.classe === p.classe);
            const loansCount = students.reduce((s, u) => s + u.prets.length, 0);

            return `
                <button type="button" class="profile-tile ${isAdmin ? 'admin-tile' : ''}" onclick="chooseProfile('${p.id}')">
                    <div class="profile-avatar-tile" style="background: ${p.bg};">${p.emoji}</div>
                    <div class="profile-name">${escapeHtml(p.teacherName)}</div>
                    <span class="class-tag">${isAdmin ? 'Toute l’école • PIN' : escapeHtml(p.classe)}</span>
                    <div class="profile-meta">${students.length} élèves • ${loansCount} prêt${loansCount > 1 ? 's' : ''}</div>
                </button>
            `;
        }).join('');

        const pinHtml = SESSION_STATE.pinPromptOpen ? `
            <form class="profile-pin-card" onsubmit="event.preventDefault(); submitAdminPin(false);">
                <div style="font-size:13px; font-weight:800; color:var(--ink);">🔒 Code PIN Admin / Bénévole BCD</div>
                <p class="muted-xs">Protège l'accès complet (Gestion, Récolement, Sauvegardes, toutes classes). <strong>Code démo : 1234</strong></p>
                <div class="profile-pin-row">
                    <input id="adminPinInput" type="password" maxlength="4" placeholder="1234" value="" autofocus>
                    <button type="submit" class="btn-forest">Déverrouiller</button>
                    <button type="button" class="btn-soft-sm" onclick="submitAdminPin(true)">⚡ Démo</button>
                </div>
                ${SESSION_STATE.pinError ? `<div style="color:var(--terracotta); font-size:11.5px; font-weight:700;">${escapeHtml(SESSION_STATE.pinError)}</div>` : ''}
            </form>
        ` : '';

        overlay.innerHTML = `
            <div class="profile-splash-inner">
                <div class="profile-splash-brand">📖 BCD Albert Camus Pasteur • Meudon</div>
                <h1 class="profile-splash-title">Qui utilise la BCD aujourd'hui ?</h1>
                <p class="profile-splash-sub">Choisissez votre classe en 1 clic (ou bipez votre badge enseignant) : vous ne voyez directement que <strong>vos propres élèves</strong>.</p>

                <div class="profile-grid">
                    ${tilesHtml}
                </div>

                ${pinHtml}

                <form class="profile-badge-scanner" onsubmit="handleBadgeLogin(event)">
                    <span class="scan-badge">⚡ Badge</span>
                    <input id="badgeLoginInput" type="text" placeholder="Bipez votre carte enseignant (ex: ENS-04, ENS-01) ou tapez un nom…">
                    <button type="submit" class="btn-forest-sm">Valider</button>
                    <button type="button" class="sc-btn" onclick="document.getElementById('badgeLoginInput').value='ENS-01'; handleBadgeLogin();">⚡ Tester bip ENS-01 (CP)</button>
                </form>

                <a href="index.html" class="muted-xs" style="text-decoration:underline; margin-top:4px;">↩ Revenir au prototype classique de l’application</a>
            </div>
        `;
    }

    function openProfilePicker() {
        SESSION_STATE.pinPromptOpen = false;
        SESSION_STATE.pinError = '';
        renderProfileSplash();
        const overlay = document.getElementById('profileSplashOverlay');
        if (overlay) overlay.classList.add('open');
    }

    function closeProfilePicker() {
        const overlay = document.getElementById('profileSplashOverlay');
        if (overlay) overlay.classList.remove('open');
    }

    function chooseProfile(profileId) {
        const profile = PROFILES_DATA.find(p => p.id === profileId);
        if (!profile) return;

        if (profile.role === 'ADMIN') {
            SESSION_STATE.pinPromptOpen = true;
            SESSION_STATE.pinError = '';
            renderProfileSplash();
            setTimeout(() => document.getElementById('adminPinInput')?.focus(), 40);
            return;
        }

        SESSION_STATE.profileId = profile.id;
        SESSION_STATE.role = 'TEACHER';
        SESSION_STATE.classe = profile.classe;
        SESSION_STATE.pinPromptOpen = false;
        SESSION_STATE.pinError = '';
        GESTION_STATE.subTab = 'eleves';
        GESTION_STATE.userClasse = profile.classe;

        closeProfilePicker();
        applySessionToUI(false);
        chime();
    }

    function submitAdminPin(directDemo) {
        const val = directDemo ? '1234' : (document.getElementById('adminPinInput')?.value || '').trim();
        if (val !== '1234') {
            SESSION_STATE.pinError = 'Code incorrect (indice démo : 1234)';
            renderProfileSplash();
            setTimeout(() => document.getElementById('adminPinInput')?.focus(), 40);
            return;
        }
        SESSION_STATE.profileId = 'admin_bcd';
        SESSION_STATE.role = 'ADMIN';
        SESSION_STATE.classe = null;
        SESSION_STATE.pinPromptOpen = false;
        SESSION_STATE.pinError = '';
        GESTION_STATE.userClasse = 'ALL';

        closeProfilePicker();
        applySessionToUI(false);
        chime();
    }

    function handleBadgeLogin(e) {
        if (e) e.preventDefault();
        const raw = (document.getElementById('badgeLoginInput')?.value || '').trim().toLowerCase();
        if (!raw) return;
        const match = PROFILES_DATA.find(p =>
            p.badgeCode.toLowerCase() === raw ||
            p.teacherName.toLowerCase().includes(raw) ||
            (p.classe && p.classe.toLowerCase() === raw)
        );
        if (!match) {
            notify(`Badge « ${escapeHtml(raw)} » non reconnu (essayez ENS-01 à ENS-05)`, '⚠️');
            return;
        }
        chooseProfile(match.id);
    }

    function renderComptoirStudentPills(selectedStudentId) {
        const bar = document.getElementById('comptoirStudentPills');
        if (!bar) return;

        if (SESSION_STATE.role === 'TEACHER') {
            const pupils = USERS_DATA.filter(u => u.type === 'ELEVE' && u.classe === SESSION_STATE.classe);
            bar.innerHTML = `
                <span class="class-pill-label">Élèves ${escapeHtml(SESSION_STATE.classe)} :</span>
                ${pupils.map(u => {
                    const activeCls = selectedStudentId === u.id ? 'active' : '';
                    const badge = u.prets.length > 0 ? ` (${u.prets.length})` : '';
                    return `<button class="class-pill ${activeCls}" onclick="openUserAtComptoir('${u.id}')">${u.avatar} ${escapeHtml(u.prenom)}${badge}</button>`;
                }).join('')}
            `;
        } else {
            const firstByClass = [
                { id: 'thomas_cp', label: 'CP (Roy)' },
                { id: 'hugo_ce1', label: 'CE1 (Blanc)' },
                { id: 'camille_ce2', label: 'CE2 (Martin)' },
                { id: 'lucas_cm1', label: 'CM1-A (Dupont)' },
                { id: 'lea_cm2', label: 'CM2-B (Simon)' }
            ];
            bar.innerHTML = `
                <span class="class-pill-label">Classes :</span>
                ${firstByClass.map(c => `<button class="class-pill ${selectedStudentId === c.id ? 'active' : ''}" onclick="openUserAtComptoir('${c.id}')">${c.label}</button>`).join('')}
            `;
        }
    }

    function applySessionToUI(silent) {
        const profile = getActiveProfile();
        const isTeacher = SESSION_STATE.role === 'TEACHER';

        const headerBtn = document.getElementById('headerProfileBtn');
        if (headerBtn) {
            headerBtn.innerText = isTeacher
                ? `${profile.emoji} ${profile.teacherName} • ${profile.classe} ▾`
                : `🛠️ Admin BCD • Toute l’école ▾`;
        }

        const kpiEl = document.getElementById('headerActiveLoansKpi');
        if (kpiEl) {
            if (isTeacher) {
                const clsLoans = USERS_DATA
                    .filter(u => u.type === 'ELEVE' && u.classe === SESSION_STATE.classe)
                    .reduce((s, u) => s + u.prets.length, 0);
                kpiEl.innerText = `📚 ${clsLoans} prêt${clsLoans > 1 ? 's' : ''} (${SESSION_STATE.classe})`;
            } else {
                const allLoans = USERS_DATA.reduce((s, u) => s + u.prets.length, 0);
                kpiEl.innerText = `📚 ${allLoans} prêts actifs (École)`;
            }
        }

        const navGestion = document.getElementById('navBtnGestion');
        if (navGestion) {
            navGestion.innerText = isTeacher ? `👧 Ma Classe (${SESSION_STATE.classe})` : '⚙️ Gestion';
        }

        const scanInp = document.getElementById('scanInputField');
        if (scanInp) {
            scanInp.placeholder = isTeacher
                ? `Bipez ou cliquez un élève de ${SESSION_STATE.classe} (ou scannez un livre)…`
                : "Bipez la carte d'un élève ou tapez un prénom (ex: Lucas, Léa)…";
        }

        const defaultStudent = isTeacher
            ? (USERS_DATA.find(u => u.type === 'ELEVE' && u.classe === SESSION_STATE.classe) || USERS_DATA[0])
            : (USERS_DATA.find(u => u.id === 'lucas_cm1') || USERS_DATA[0]);

        renderComptoirStudentPills(defaultStudent.id);
        openUserAtComptoir(defaultStudent.id, true);

        if (activeMainView === 'livres') renderLivresView();
        else if (activeMainView === 'mallettes') renderMallettesView();
        else if (activeMainView === 'gestion') renderGestionView();

        if (!silent) {
            notify(
                isTeacher
                    ? `Session <strong>${escapeHtml(profile.teacherName)} (${escapeHtml(profile.classe)})</strong> — affichage filtré sur vos élèves`
                    : 'Mode <strong>Admin / Bénévole BCD</strong> activé (accès complet)',
                profile.emoji
            );
        }
    }

    function openClassReminderCouponsModal(classe) {
        const pupils = USERS_DATA.filter(u => u.type === 'ELEVE' && u.classe === classe && u.prets.length > 0);
        openModal(
            `🖨️ Coupons cahier de liaison — Classe ${escapeHtml(classe)}`,
            `<div class="a4-preview-sheet">
                <p class="muted-xs" style="margin-bottom:10px;">Coupons bienveillants prêts à découper et coller dans le cahier de liaison :</p>
                ${pupils.length === 0
                    ? `<div class="alert-inline-ok">✨ Aucun livre en cours ou en retard dans la classe ${escapeHtml(classe)}.</div>`
                    : `<div class="a4-sheet-grid" style="grid-template-columns: repeat(2, 1fr);">
                        ${pupils.map(u => `
                            <div class="a4-cell" style="text-align:left; align-items:flex-start; padding:9px;">
                                <div class="label-prev-header">📖 BCD ALBERT CAMUS • CLASSE ${escapeHtml(classe)}</div>
                                <strong style="font-size:11.5px;">Bonjour les parents de ${escapeHtml(u.prenom)},</strong>
                                <span style="font-size:10.5px; color:var(--ink);">Petit rappel : ${escapeHtml(u.prenom)} a actuellement à la maison :</span>
                                <span style="font-size:10.5px; font-weight:700; color:var(--forest);">• ${u.prets.map(p => escapeHtml(p.titre)).join(', ')}</span>
                                <span class="muted-xs">Merci de le glisser dans le cartable pour le prochain passage en BCD !</span>
                            </div>
                        `).join('')}
                    </div>`
                }
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Fermer</button>
             <button class="btn-forest" onclick="closeModal(); chime(); notify('Planche de coupons ${escapeHtml(classe)} envoyée à l’impression', '🖨️')">🖨️ Imprimer la planche</button>`
        );
    }

    function openUserAtComptoir(userId, silent) {
        const user = USERS_DATA.find(u => u.id === userId);
        if (!user) return;
        if (!silent) {
            switchMainView('comptoir');
            chime();
        }
        renderComptoirStudentPills(user.id);

        const av = document.getElementById('studentAvatar');
        const nm = document.getElementById('studentName');
        const sub = document.getElementById('studentSub');
        const ql = document.getElementById('quotaLabel');
        const qb = document.getElementById('quotaBar');

        if (av) av.innerText = user.avatar;
        if (nm) nm.innerHTML = `${escapeHtml(user.prenom)} ${escapeHtml(user.nom)} <span class="class-tag" id="studentClass">${escapeHtml(user.classe)}</span>`;
        if (sub) sub.innerText = `${user.type === 'ENSEIGNANT' ? 'Carte Enseignant' : 'Carte Élève'} (${user.numero}) • Dernier passage : ${user.dernierPassage}`;

        const count = user.prets.length;
        const pct = Math.min(100, Math.round((count / user.quotaMax) * 100));
        if (ql) ql.innerText = `${count} / ${user.quotaMax} livre${count > 1 ? 's' : ''} emprunté${count > 1 ? 's' : ''}`;
        if (qb) {
            qb.style.width = `${pct}%`;
            qb.style.background = count >= user.quotaMax ? '#D49B28' : 'var(--forest)';
        }

        const area = document.querySelector('#view-comptoir .student-loans-area');
        if (area) {
            const pendingCard = document.getElementById('pendingLoanCard');
            const remaining = Math.max(0, user.quotaMax - count);
            let html = `
                <div class="area-title">
                    <span>Livres actuellement dans son sac (${count})</span>
                    <span style="color: var(--forest); font-weight: 700;">${remaining} place${remaining > 1 ? 's' : ''} restante${remaining > 1 ? 's' : ''}</span>
                </div>
            `;
            if (count === 0) {
                html += `
                    <div class="empty-inline-box">
                        <span>🎒</span>
                        <div>
                            <strong>Sac vide pour le moment</strong>
                            <p>Bipez un livre ci-dessus pour l'ajouter au sac de ${escapeHtml(user.prenom)}.</p>
                        </div>
                    </div>
                `;
            } else {
                user.prets.forEach((p, idx) => {
                    const itemId = `dynLoan_${user.id}_${idx}`;
                    html += `
                        <div class="book-card-item ${p.retard ? 'late' : ''}" id="${itemId}">
                            <div class="book-cover" style="background: ${p.gradient};">${p.emoji}</div>
                            <div class="book-info">
                                <div class="book-title">${escapeHtml(p.titre)}</div>
                                <div class="book-meta">${escapeHtml(p.auteur)} • Cote ${escapeHtml(p.cote)} (${escapeHtml(p.exId)})</div>
                                <span class="book-due-pill">${p.retard ? '⏰ ' : '⏳ À rendre le '}${escapeHtml(p.echeance)}</span>
                            </div>
                            <button class="btn-return-book" onclick="returnStudentBook('${itemId}', '${escapeHtml(p.titre).replace(/'/g, "\\'")}', '${p.zoneKey}')">
                                Rendre
                            </button>
                        </div>
                    `;
                });
            }
            area.innerHTML = html;
            if (pendingCard) area.appendChild(pendingCard);
        }

        if (!silent) {
            notify(`Sac ouvert pour <strong>${escapeHtml(user.prenom)} ${escapeHtml(user.nom)} (${escapeHtml(user.classe)})</strong>`, user.avatar);
        }
    }

    // ------------------------------------------------------------------------
    // 6. VUE 📚 LES LIVRES
    // ------------------------------------------------------------------------
    function getFilteredBooks() {
        const q = LIVRES_STATE.search.trim().toLowerCase();
        return CATALOG_BOOKS.filter(b => {
            if (LIVRES_STATE.onlyZero && b.exemplaires.length > 0) return false;
            if (LIVRES_STATE.genre !== 'ALL' && b.genre !== LIVRES_STATE.genre) return false;
            if (LIVRES_STATE.theme !== 'ALL' && b.theme !== LIVRES_STATE.theme) return false;
            if (LIVRES_STATE.niveau !== 'ALL' && b.niveau !== LIVRES_STATE.niveau) return false;
            if (q) {
                const hay = `${b.titre} ${b.auteur} ${b.isbn} ${b.cote} ${b.genre} ${b.theme} ${b.niveau} ${b.zoneLabel}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });
    }

    function renderLivresView() {
        const root = document.getElementById('view-livres');
        if (!root) return;

        const genres = [...new Set(CATALOG_BOOKS.map(b => b.genre))].sort();
        const themes = [...new Set(CATALOG_BOOKS.map(b => b.theme))].sort();
        const niveaux = [...new Set(CATALOG_BOOKS.map(b => b.niveau))].sort();
        const filtered = getFilteredBooks();
        const zeroCount = CATALOG_BOOKS.filter(b => b.exemplaires.length === 0).length;

        let selected = CATALOG_BOOKS.find(b => b.id === LIVRES_STATE.selectedBookId) || filtered[0] || null;
        if (selected) LIVRES_STATE.selectedBookId = selected.id;

        const listHtml = filtered.length === 0
            ? `<div class="empty-state-card"><span style="font-size:32px;">🔍</span><strong>Aucun livre ne correspond aux filtres</strong><button class="btn-soft" onclick="resetLivresFilters()">Réinitialiser les filtres</button></div>`
            : (LIVRES_STATE.viewMode === 'grid'
                ? `<div class="catalog-grid">${filtered.map(b => {
                    const total = b.exemplaires.length;
                    const dispo = b.exemplaires.filter(e => e.statut === 'DISPONIBLE').length;
                    const badgeCls = total === 0 ? 'pill-terracotta' : (dispo > 0 ? 'pill-forest' : 'pill-gold');
                    const badgeTxt = total === 0 ? '⚠️ 0 exemplaire' : `${dispo}/${total} dispo`;
                    const isSel = selected && selected.id === b.id;
                    return `
                        <div class="catalog-book-card ${isSel ? 'selected' : ''}" onclick="selectCatalogBook('${b.id}')">
                            <div class="catalog-card-top">
                                <div class="book-cover-lg" style="background:${b.gradient};">${b.emoji}</div>
                                <div style="flex:1; min-width:0;">
                                    <div class="catalog-card-title">${escapeHtml(b.titre)}</div>
                                    <div class="catalog-card-author">${escapeHtml(b.auteur)}</div>
                                    <div class="catalog-tags-row">
                                        <span class="tag-pill">${escapeHtml(b.genre)}</span>
                                        <span class="tag-pill soft">${escapeHtml(b.cote)}</span>
                                    </div>
                                </div>
                            </div>
                            <div class="catalog-card-footer">
                                <span class="zone-mini-tag">📍 ${escapeHtml(b.zoneLabel)}</span>
                                <span class="status-pill ${badgeCls}">${badgeTxt}</span>
                            </div>
                        </div>
                    `;
                }).join('')}</div>`
                : `<div class="catalog-table-wrap"><table class="warm-table">
                    <thead>
                        <tr><th>Titre & Auteur</th><th>Genre</th><th>Niveau</th><th>Rayon BCD</th><th>Exemplaires</th></tr>
                    </thead>
                    <tbody>
                        ${filtered.map(b => {
                            const total = b.exemplaires.length;
                            const dispo = b.exemplaires.filter(e => e.statut === 'DISPONIBLE').length;
                            const isSel = selected && selected.id === b.id;
                            return `
                                <tr class="${isSel ? 'selected' : ''}" onclick="selectCatalogBook('${b.id}')">
                                    <td><strong>${b.emoji} ${escapeHtml(b.titre)}</strong><br><span class="muted-xs">${escapeHtml(b.auteur)} • ${escapeHtml(b.isbn)}</span></td>
                                    <td><span class="tag-pill">${escapeHtml(b.genre)}</span></td>
                                    <td>${escapeHtml(b.niveau)}</td>
                                    <td><span class="muted-xs">${escapeHtml(b.zoneLabel)}</span></td>
                                    <td><span class="status-pill ${total === 0 ? 'pill-terracotta' : (dispo > 0 ? 'pill-forest' : 'pill-gold')}">${total === 0 ? '0 ex.' : `${dispo}/${total} dispo`}</span></td>
                                </tr>
                            `;
                        }).join('')}
                    </tbody>
                </table></div>`
            );

        const detailHtml = !selected
            ? `<div class="empty-state-card"><span style="font-size:38px;">📖</span><strong>Sélectionnez un livre</strong><p class="muted-xs">Cliquez sur une carte à gauche pour afficher sa fiche complète et localiser son rayon.</p></div>`
            : (() => {
                const total = selected.exemplaires.length;
                const dispo = selected.exemplaires.filter(e => e.statut === 'DISPONIBLE').length;
                return `
                    <div class="detail-panel-inner">
                        <div class="detail-hero">
                            <div class="book-cover-xl" style="background:${selected.gradient};">${selected.emoji}</div>
                            <div style="flex:1; min-width:0;">
                                <div style="display:flex; justify-content:space-between; align-items:flex-start; gap:8px;">
                                    <div>
                                        <h2 class="detail-book-title">${escapeHtml(selected.titre)}</h2>
                                        <p class="detail-book-author">${escapeHtml(selected.auteur)} • ${escapeHtml(selected.editeur)}</p>
                                    </div>
                                    <button class="btn-soft-sm" onclick="sendBookToEtiquettes('${selected.id}')" title="Imprimer l'étiquette">🏷️</button>
                                </div>
                                <div class="catalog-tags-row" style="margin-top:8px;">
                                    <span class="tag-pill">${escapeHtml(selected.genre)}</span>
                                    <span class="tag-pill gold">${escapeHtml(selected.theme)}</span>
                                    <span class="tag-pill soft">${escapeHtml(selected.niveau)}</span>
                                    ${selected.malletteCode ? `<span class="tag-pill purple">🎒 ${escapeHtml(selected.malletteCode)}</span>` : ''}
                                </div>
                            </div>
                        </div>

                        <div class="locate-callout-card">
                            <div>
                                <div class="muted-xs" style="text-transform:uppercase; font-weight:800;">Emplacement en BCD</div>
                                <strong style="color:var(--forest); font-size:13px;">📍 ${escapeHtml(selected.zoneLabel)}</strong>
                                <span class="muted-xs" style="display:block;">Cote ${escapeHtml(selected.cote)} • ISBN ${escapeHtml(selected.isbn)}</span>
                            </div>
                            <button class="btn-forest" onclick="locateBookOnComptoir('${selected.id}')">
                                🗺️ Localiser sur le plan BCD
                            </button>
                        </div>

                        <div class="detail-section-card">
                            <div class="section-row-head">
                                <h4>📦 Exemplaires physiques <span class="status-pill ${total === 0 ? 'pill-terracotta' : 'pill-forest'}">${dispo}/${total} dispo</span></h4>
                                <button class="btn-forest-sm" onclick="addPhysicalCopy('${selected.id}')">➕ Exemplaire</button>
                            </div>
                            ${total === 0
                                ? `<div class="alert-inline-warn">⚠️ Ce titre n'a aucun exemplaire physique rattaché. Ajoutez-en un ou vérifiez la boîte à anomalies.</div>`
                                : `<div class="copies-list">
                                    ${selected.exemplaires.map(ex => `
                                        <div class="copy-row">
                                            <span class="mono-badge">${escapeHtml(ex.id)}</span>
                                            <span class="status-pill ${ex.statut === 'DISPONIBLE' ? 'pill-forest' : 'pill-gold'}">${ex.statut === 'DISPONIBLE' ? 'Disponible' : 'Emprunté'}</span>
                                            <span class="muted-xs" style="flex:1;">${ex.emprunteur ? escapeHtml(ex.emprunteur) : escapeHtml(ex.etat)}</span>
                                            <span class="muted-xs">${escapeHtml(ex.dateAjout)}</span>
                                        </div>
                                    `).join('')}
                                </div>`
                            }
                        </div>

                        <div class="detail-section-card">
                            <div class="section-row-head">
                                <h4>📚 Ressources pédagogiques</h4>
                                <button class="btn-soft-sm" onclick="addBookResource('${selected.id}')">➕ Ajouter</button>
                            </div>
                            ${selected.ressources.length === 0
                                ? `<p class="muted-xs">Aucune fiche associée pour ce titre.</p>`
                                : `<ul class="resource-list">${selected.ressources.map(r => `<li>📄 ${escapeHtml(r)}</li>`).join('')}</ul>`
                            }
                        </div>
                    </div>
                `;
            })();

        root.innerHTML = `
            <div class="subpage-header">
                <div>
                    <h2>📚 Le Catalogue des Livres</h2>
                    <p>${CATALOG_BOOKS.length} titres catalogués • ${CATALOG_BOOKS.reduce((s, b) => s + b.exemplaires.length, 0)} exemplaires physiques en BCD</p>
                </div>
                <div class="header-actions">
                    <button class="btn-forest" onclick="openUnifiedBookIntake()">⚡➕ Ajouter des livres (Douchette • API • Manuel)</button>
                    ${SESSION_STATE.role === 'ADMIN' ? '<button class="btn-soft" onclick="openImportLivresCsvModal()">📥 Importer CSV</button>' : ''}
                </div>
            </div>

            <div class="toolbar-card">
                <div class="toolbar-row">
                    <div class="search-wrap" style="flex:1;">
                        <span>🔍</span>
                        <input id="livresSearchInput" type="text" value="${escapeHtml(LIVRES_STATE.search)}"
                            placeholder="Rechercher un livre, un auteur, un ISBN, une cote, un rayon…"
                            oninput="onLivresSearch(this.value)">
                    </div>
                    <div class="view-switch-tabs">
                        <button class="tab-btn ${LIVRES_STATE.viewMode === 'grid' ? 'active' : ''}" onclick="setLivresViewMode('grid')">⊞ Cartes</button>
                        <button class="tab-btn ${LIVRES_STATE.viewMode === 'list' ? 'active' : ''}" onclick="setLivresViewMode('list')">☰ Liste</button>
                    </div>
                </div>
                <div class="toolbar-filters">
                    <label>Genre :
                        <select onchange="LIVRES_STATE.genre=this.value; renderLivresView();">
                            <option value="ALL">Tous les genres</option>
                            ${genres.map(g => `<option value="${escapeHtml(g)}" ${LIVRES_STATE.genre === g ? 'selected' : ''}>${escapeHtml(g)}</option>`).join('')}
                        </select>
                    </label>
                    <label>Thème :
                        <select onchange="LIVRES_STATE.theme=this.value; renderLivresView();">
                            <option value="ALL">Tous les thèmes</option>
                            ${themes.map(t => `<option value="${escapeHtml(t)}" ${LIVRES_STATE.theme === t ? 'selected' : ''}>${escapeHtml(t)}</option>`).join('')}
                        </select>
                    </label>
                    <label>Niveau :
                        <select onchange="LIVRES_STATE.niveau=this.value; renderLivresView();">
                            <option value="ALL">Tous les niveaux</option>
                            ${niveaux.map(n => `<option value="${escapeHtml(n)}" ${LIVRES_STATE.niveau === n ? 'selected' : ''}>${escapeHtml(n)}</option>`).join('')}
                        </select>
                    </label>
                    <button class="sc-btn ${LIVRES_STATE.onlyZero ? 'primary' : ''}" onclick="LIVRES_STATE.onlyZero=!LIVRES_STATE.onlyZero; renderLivresView();">
                        ⚠️ 0 exemplaire (${zeroCount})
                    </button>
                    <button class="sc-btn" onclick="resetLivresFilters()">🔄 Réinitialiser</button>
                    <span class="muted-xs" style="margin-left:auto;">${filtered.length} résultat${filtered.length > 1 ? 's' : ''}</span>
                </div>
            </div>

            <div class="split-layout split-60-40">
                <div class="split-left panel-scroll">${listHtml}</div>
                <div class="split-right detail-panel panel-scroll">${detailHtml}</div>
            </div>
        `;
    }

    function onLivresSearch(val) {
        LIVRES_STATE.search = val;
        const pos = document.getElementById('livresSearchInput')?.selectionStart || val.length;
        renderLivresView();
        const inp = document.getElementById('livresSearchInput');
        if (inp) {
            inp.focus();
            inp.setSelectionRange(pos, pos);
        }
    }

    function setLivresViewMode(mode) {
        LIVRES_STATE.viewMode = mode;
        renderLivresView();
    }

    function selectCatalogBook(id) {
        LIVRES_STATE.selectedBookId = id;
        renderLivresView();
    }

    function resetLivresFilters() {
        LIVRES_STATE.search = '';
        LIVRES_STATE.genre = 'ALL';
        LIVRES_STATE.theme = 'ALL';
        LIVRES_STATE.niveau = 'ALL';
        LIVRES_STATE.onlyZero = false;
        renderLivresView();
    }

    function addPhysicalCopy(bookId) {
        const book = CATALOG_BOOKS.find(b => b.id === bookId);
        if (!book) return;
        const nextNum = 180 + Math.floor(Math.random() * 800);
        const newId = `#${nextNum}`;
        book.exemplaires.push({
            id: newId,
            statut: 'DISPONIBLE',
            dateAjout: "Aujourd'hui",
            etat: 'Neuf'
        });
        chime();
        rebuildRecolementItems();
        renderLivresView();
        notify(`Exemplaire <strong>${newId}</strong> ajouté à « ${escapeHtml(book.titre)} »`, '📦');
    }

    function addBookResource(bookId) {
        const book = CATALOG_BOOKS.find(b => b.id === bookId);
        if (!book) return;
        openModal(
            `📚 Ajouter une ressource — ${escapeHtml(book.titre)}`,
            `<div class="form-stack">
                <label>Intitulé de la fiche ou ressource
                    <input id="modalResTitle" type="text" placeholder="Ex: Questionnaire rallye lecture CE2">
                </label>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Annuler</button>
             <button class="btn-forest" onclick="confirmAddBookResource('${book.id}')">Enregistrer</button>`
        );
    }

    function confirmAddBookResource(bookId) {
        const book = CATALOG_BOOKS.find(b => b.id === bookId);
        const val = (document.getElementById('modalResTitle')?.value || '').trim();
        if (!book || !val) return;
        book.ressources.push(val);
        closeModal();
        chime();
        renderLivresView();
        notify('Ressource pédagogique ajoutée', '📚');
    }

    // ------------------------------------------------------------------------
    // 6B. STUDIO UNIFIÉ D'AJOUT DE LIVRES (DOUCHETTE • API • MANUEL / SANS CODE)
    // ------------------------------------------------------------------------
    const API_SIMULATED_BOOKS = [
        {
            titre: 'Le Loup qui voulait faire le tour du monde',
            auteur: 'Orianne Lallemand & Éléonore Thuillier',
            editeur: 'Éditions Auzou',
            isbn: '9782733822142',
            cote: 'A LAL',
            genre: 'Album',
            theme: 'Voyage & Aventures',
            niveau: 'Cycle 2 (CP-CE1)',
            zoneKey: '6_haut',
            zoneLabel: 'Îlot 6 • Face Avant (Albums A–T)',
            emoji: '🐺',
            gradient: 'linear-gradient(135deg, #2A5C8A, #3B82F6)',
            source: 'BNF',
            sourceClass: 'bnf'
        },
        {
            titre: 'Matilda',
            auteur: 'Roald Dahl',
            editeur: 'Gallimard Jeunesse',
            isbn: '9782070518425',
            cote: 'R DAH',
            genre: 'Roman',
            theme: 'Humour & Fantaisie',
            niveau: 'Cycle 3 (CM1-CM2)',
            zoneKey: '1_droite',
            zoneLabel: 'Étagère 1 • Face Droite (Romans A–P)',
            emoji: '📚',
            gradient: 'linear-gradient(135deg, #D49B28, #F59E0B)',
            source: 'Google Books',
            sourceClass: 'google'
        },
        {
            titre: 'Chien Bleu',
            auteur: 'Nadja',
            editeur: "L'école des loisirs",
            isbn: '9782211056786',
            cote: 'A NAD',
            genre: 'Album',
            theme: 'Imaginaire & Amitié',
            niveau: 'Cycle 2 (CP-CE1)',
            zoneKey: '6_haut',
            zoneLabel: 'Îlot 6 • Face Avant (Albums A–T)',
            emoji: '🐕',
            gradient: 'linear-gradient(135deg, #1E3A8A, #0284C7)',
            source: 'BNF',
            sourceClass: 'bnf'
        },
        {
            titre: 'Le Petit Nicolas',
            auteur: 'René Goscinny & Jean-Jacques Sempé',
            editeur: 'Folio Junior / IMAV',
            isbn: '9782070541270',
            cote: 'R GOS',
            genre: 'Roman',
            theme: 'École & Humour',
            niveau: 'Cycle 3 (CM1-CM2)',
            zoneKey: '3',
            zoneLabel: 'Mur 3 • Séries & Classiques',
            emoji: '🎒',
            gradient: 'linear-gradient(135deg, #C85434, #EA580C)',
            source: 'Google Books',
            sourceClass: 'google'
        },
        {
            titre: "Mortelle Adèle — T.1 Tout ça finira mal",
            auteur: 'Mr Tan & Miss Prickly',
            editeur: 'Bayard / Tourbillon',
            isbn: '9782848017235',
            cote: 'BD TAN',
            genre: 'BD',
            theme: 'Humour & École',
            niveau: 'Cycle 2 & 3',
            zoneKey: '2',
            zoneLabel: 'Bac BD 2 • Niveau 1 (Supérieur)',
            emoji: '😈',
            gradient: 'linear-gradient(135deg, #BE123C, #F43F5E)',
            source: 'OpenLibrary',
            sourceClass: 'openlib'
        },
        {
            titre: 'Histoire de Babar, le petit éléphant',
            auteur: 'Jean de Brunhoff',
            editeur: 'Hachette Jeunesse',
            isbn: '9782012011458',
            cote: 'A BRU',
            genre: 'Album',
            theme: 'Classiques & Animaux',
            niveau: 'Cycle 2 (CP-CE1)',
            zoneKey: '8_gauche',
            zoneLabel: 'Biblio Verte 8 • Face Gauche (Côté Bureau)',
            emoji: '🐘',
            gradient: 'linear-gradient(135deg, #1E5139, #10B981)',
            source: 'BNF',
            sourceClass: 'bnf'
        },
        {
            titre: "Les Volcans — Mes p'tits docs",
            auteur: 'Stéphanie Ledu',
            editeur: 'Milan Jeunesse',
            isbn: '9782745951236',
            cote: '551 LED',
            genre: 'Documentaire',
            theme: 'Sciences & Terre',
            niveau: 'Cycle 2 (CP-CE2)',
            zoneKey: '8_droite',
            zoneLabel: 'Biblio Verte 8 • Face Droite (Côté Tapis)',
            emoji: '🌋',
            gradient: 'linear-gradient(135deg, #B45309, #DC2626)',
            source: 'Google Books',
            sourceClass: 'google'
        },
        {
            titre: 'Les Trois Brigands',
            auteur: 'Tomi Ungerer',
            editeur: "L'école des loisirs",
            isbn: '9782211034562',
            cote: 'A UNG',
            genre: 'Album',
            theme: 'Contes & Aventure',
            niveau: 'Cycle 2 (CP-CE1)',
            zoneKey: '6_haut',
            zoneLabel: 'Îlot 6 • Face Avant (Albums A–T)',
            emoji: '🎩',
            gradient: 'linear-gradient(135deg, #334155, #0F172A)',
            source: 'BNF',
            sourceClass: 'bnf'
        }
    ];

    const INTAKE_STATE = {
        query: '',
        rafale: true,
        noBarcode: false,
        manualExpanded: false,
        camusCounter: 42,
        sessionItems: []
    };

    function nextCamusCode() {
        INTAKE_STATE.camusCounter += 1;
        return `CAMUS-${String(INTAKE_STATE.camusCounter).padStart(4, '0')}`;
    }

    function normalizeCode(raw) {
        return String(raw || '').replace(/[\s-]+/g, '').trim();
    }

    function isLikelyIsbn(raw) {
        const clean = normalizeCode(raw);
        return /^(97[89])?\d{9}[\dXx]$/.test(clean);
    }

    function registerAddedBook(payload, sourceTag, qty, forceNoBarcode) {
        const count = Math.max(1, Math.min(10, parseInt(qty || 1, 10)));
        const cleanCode = forceNoBarcode ? nextCamusCode() : normalizeCode(payload.isbn || nextCamusCode());
        let book = !forceNoBarcode ? CATALOG_BOOKS.find(b => normalizeCode(b.isbn) === cleanCode) : null;

        const createdExIds = [];
        const baseSeq = 210 + CATALOG_BOOKS.reduce((s, b) => s + b.exemplaires.length, 0);

        if (book) {
            for (let i = 0; i < count; i++) {
                const exId = `#${baseSeq + i}`;
                book.exemplaires.push({ id: exId, statut: 'DISPONIBLE', dateAjout: "Aujourd'hui", etat: 'Neuf' });
                createdExIds.push(exId);
            }
        } else {
            const newId = 'bk-' + (CATALOG_BOOKS.length + 1);
            const exemplaires = [];
            for (let i = 0; i < count; i++) {
                const exId = `#${baseSeq + i}`;
                exemplaires.push({ id: exId, statut: 'DISPONIBLE', dateAjout: "Aujourd'hui", etat: forceNoBarcode ? 'Fonds ancien' : 'Neuf' });
                createdExIds.push(exId);
            }
            const genre = payload.genre || 'Album';
            const auteur = payload.auteur || 'Auteur jeunesse';
            book = {
                id: newId,
                titre: payload.titre || 'Ouvrage sans titre',
                auteur,
                editeur: payload.editeur || 'Éditions Jeunesse',
                isbn: cleanCode,
                cote: payload.cote || `${genre[0].toUpperCase()} ${auteur.slice(0, 3).toUpperCase()}`,
                genre,
                theme: payload.theme || 'Nouvelles acquisitions',
                niveau: payload.niveau || 'Cycle 2 (CP-CE2)',
                emoji: payload.emoji || (genre === 'BD' ? '💥' : (genre === 'Documentaire' ? '🔬' : (genre === 'Contes' ? '🧚' : '📘'))),
                gradient: payload.gradient || 'linear-gradient(135deg, #1E5139, #2A5C8A)',
                zoneKey: payload.zoneKey || '6_haut',
                zoneLabel: payload.zoneLabel || 'Îlot 6 • Face Avant (Albums A–T)',
                malletteCode: null,
                addedOrder: CATALOG_BOOKS.length + 20,
                ressources: [],
                exemplaires
            };
            CATALOG_BOOKS.unshift(book);
        }

        if (forceNoBarcode) {
            const prev = ETIQUETTES_STATE.selected[book.id];
            ETIQUETTES_STATE.selected[book.id] = { checked: true, qty: (prev?.qty || 0) + count };
        }

        INTAKE_STATE.sessionItems.unshift({
            sessionId: 'si-' + Date.now() + '-' + Math.floor(Math.random() * 1000),
            bookId: book.id,
            titre: book.titre,
            auteur: book.auteur,
            code: book.isbn,
            zoneLabel: book.zoneLabel,
            emoji: book.emoji,
            gradient: book.gradient,
            sourceTag,
            noBarcode: !!forceNoBarcode,
            exIds: createdExIds
        });

        LIVRES_STATE.selectedBookId = book.id;
        rebuildRecolementItems();
        renderLivresView();
        chime();
        return book;
    }

    function openUnifiedBookIntake() {
        openModal(
            '⚡➕ Ajouter des livres — Douchette • Recherche API • Saisie manuelle',
            buildUnifiedIntakeShellHtml(),
            `<span class="muted-xs" style="margin-right:auto;">💡 Un seul écran : scannez un code-barres, cherchez par titre/auteur (BNF, Google Books) ou saisissez à la main.</span>
             <button class="btn-soft" onclick="closeModal()">Fermer</button>`,
            'modal-card-wide'
        );
        updateIntakeApiResults();
        updateIntakeSessionPanel();
        setTimeout(() => {
            const inp = document.getElementById('uniIntakeInput');
            if (inp) inp.focus();
        }, 40);
    }

    function buildUnifiedIntakeShellHtml() {
        return `
            <div class="intake-layout">
                <div class="intake-left">
                    <form class="intake-omnibar" onsubmit="handleIntakeSubmit(event)">
                        <span class="scan-badge">⚡ Scan / API</span>
                        <input id="uniIntakeInput" type="text" value="${escapeHtml(INTAKE_STATE.query)}"
                            placeholder="Bipez un code-barres ISBN ou tapez un titre / auteur (ex: Loup, Matilda, Babar)…"
                            autocomplete="off" oninput="onIntakeInput(this.value)">
                        <button type="submit" class="btn-forest-sm">Valider (Entrée)</button>
                    </form>

                    <div class="intake-controls-row">
                        <label class="rafale-toggle" title="Si coché, biper un ISBN connu ou trouvé ajoute +1 exemplaire immédiatement">
                            <input type="checkbox" ${INTAKE_STATE.rafale ? 'checked' : ''} onchange="INTAKE_STATE.rafale=this.checked;">
                            <span>⚡ Mode Rafale Douchette</span>
                        </label>
                        <div class="intake-demo-chips">
                            <span class="muted-xs" style="font-weight:700;">Tester :</span>
                            <button type="button" class="sc-btn" onclick="intakeDemo('isbn_known')">⚡ Bip ISBN connu</button>
                            <button type="button" class="sc-btn" onclick="intakeDemo('isbn_new')">⚡ Bip Matilda</button>
                            <button type="button" class="sc-btn" onclick="intakeDemo('api_title')">🔍 Chercher "Loup"</button>
                            <button type="button" class="sc-btn" onclick="intakeDemo('no_barcode')">🏷️ "Babar" sans code</button>
                            <button type="button" class="sc-btn" onclick="intakeDemo('manual_unknown')">✍️ Introuvable</button>
                        </div>
                    </div>

                    <div id="intakeApiResults" class="intake-api-list"></div>

                    <div id="intakeManualWrap" class="intake-manual-card">
                        <div class="section-row-head">
                            <h4>✍️ Saisie manuelle ou ajustement (avec / sans code-barres)</h4>
                            <span class="muted-xs" id="intakeManualStatus">Prêt</span>
                        </div>

                        <label class="intake-no-barcode-bar">
                            <span style="display:flex; align-items:center; gap:7px;">
                                <input id="uniNoBarcodeChk" type="checkbox" ${INTAKE_STATE.noBarcode ? 'checked' : ''} onchange="toggleIntakeNoBarcode(this.checked)">
                                <span>🏷️ Livre sans code-barres (générer un code interne CAMUS-xxxx + étiquette A4)</span>
                            </span>
                            <span class="tag-pill gold" id="uniNoBarcodeTag">${INTAKE_STATE.noBarcode ? 'Étiquette auto' : 'Optionnel'}</span>
                        </label>

                        <div class="form-stack">
                            <div class="inline-form-row">
                                <label style="flex:1.4;">Titre de l'ouvrage
                                    <input id="uniManTitre" type="text" placeholder="Ex: Recueil de poèmes CM1">
                                </label>
                                <label style="flex:1;">Auteur
                                    <input id="uniManAuteur" type="text" placeholder="Ex: Jacques Prévert">
                                </label>
                            </div>
                            <div class="inline-form-row">
                                <label style="flex:1;">ISBN ou Code interne BCD
                                    <input id="uniManIsbn" type="text" placeholder="Ex: 978... ou CAMUS-0043">
                                </label>
                                <label style="flex:0.8;">Genre
                                    <select id="uniManGenre">
                                        <option>Album</option>
                                        <option>Roman</option>
                                        <option>BD</option>
                                        <option>Documentaire</option>
                                        <option>Contes</option>
                                    </select>
                                </label>
                                <label style="flex:1.2;">Rayon BCD
                                    <select id="uniManZone">
                                        <option value="1_droite">Étagère 1 • Face Droite (Romans A-P)</option>
                                        <option value="1_gauche">Étagère 1 • Face Gauche (Contes)</option>
                                        <option value="2">Bac BD 2 • Bandes Dessinées</option>
                                        <option value="3">Grand linéaire 3 • Mur Séries/Histoire</option>
                                        <option value="6_haut">Îlot 6 • Face Avant (Albums A-T)</option>
                                        <option value="8_droite">Biblio Verte 8 • Sciences/Dinos</option>
                                        <option value="8_gauche">Biblio Verte 8 • Premières lectures</option>
                                    </select>
                                </label>
                                <label style="width:76px;">Qté
                                    <input id="uniManQty" type="number" min="1" max="10" value="1">
                                </label>
                            </div>
                            <div style="display:flex; justify-content:flex-end; gap:8px;">
                                <button type="button" class="btn-soft-sm" onclick="clearUnifiedManualForm()">Effacer</button>
                                <button type="button" class="btn-forest" onclick="submitUnifiedManualBook()">✓ Ajouter à la BCD</button>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="intake-session-panel">
                    <div class="section-row-head" id="intakeSessionHeader">
                        <h4>📦 Pile ajoutée cette session (0)</h4>
                    </div>
                    <div id="intakeSessionList" class="intake-session-list"></div>
                    <button id="btnSessionEtiquettes" type="button" class="btn-forest" style="width:100%; justify-content:center;" onclick="sendSessionToEtiquettes()">
                        🏷️ Imprimer les étiquettes de cette session (0)
                    </button>
                </div>
            </div>
        `;
    }

    function onIntakeInput(val) {
        INTAKE_STATE.query = val;
        updateIntakeApiResults();
    }

    function updateIntakeApiResults() {
        const box = document.getElementById('intakeApiResults');
        if (!box) return;
        const raw = INTAKE_STATE.query.trim();
        const q = raw.toLowerCase();
        const qClean = normalizeCode(raw);

        if (!raw) {
            box.innerHTML = `
                <div class="alert-inline-info" style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                    <span>💡 <strong>Astuce :</strong> scannez un ISBN au bip, tapez un titre (ex: <em>Loup</em>, <em>Babar</em>, <em>Nicolas</em>) pour interroger BNF / Google Books, ou remplissez dessous.</span>
                </div>
            `;
            return;
        }

        const existingMatches = CATALOG_BOOKS.filter(b =>
            normalizeCode(b.isbn) === qClean ||
            b.titre.toLowerCase().includes(q) ||
            b.auteur.toLowerCase().includes(q)
        );
        const apiMatches = API_SIMULATED_BOOKS.filter(a =>
            !CATALOG_BOOKS.some(b => normalizeCode(b.isbn) === normalizeCode(a.isbn)) &&
            (normalizeCode(a.isbn) === qClean || a.titre.toLowerCase().includes(q) || a.auteur.toLowerCase().includes(q))
        );

        let html = '';

        existingMatches.slice(0, 2).forEach(b => {
            html += `
                <div class="intake-api-card">
                    <div class="book-cover" style="background:${b.gradient}; width:34px; height:46px; font-size:16px;">${b.emoji}</div>
                    <div style="flex:1; min-width:0;">
                        <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
                            <span class="source-badge bcd">✓ Déjà en BCD (${b.exemplaires.length} ex.)</span>
                            <strong style="font-size:12.5px;">${escapeHtml(b.titre)}</strong>
                        </div>
                        <div class="muted-xs">${escapeHtml(b.auteur)} • ${escapeHtml(b.isbn)} • ${escapeHtml(b.zoneLabel)}</div>
                    </div>
                    <div class="intake-api-actions">
                        <button type="button" class="btn-forest-sm" onclick="addExistingCatalogCopyFromIntake('${b.id}')">+1 exemplaire</button>
                    </div>
                </div>
            `;
        });

        apiMatches.slice(0, 4).forEach((a, idx) => {
            html += `
                <div class="intake-api-card">
                    <div class="book-cover" style="background:${a.gradient}; width:34px; height:46px; font-size:16px;">${a.emoji}</div>
                    <div style="flex:1; min-width:0;">
                        <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
                            <span class="source-badge ${a.sourceClass}">${escapeHtml(a.source)}</span>
                            <strong style="font-size:12.5px;">${escapeHtml(a.titre)}</strong>
                        </div>
                        <div class="muted-xs">${escapeHtml(a.auteur)} • ${escapeHtml(a.editeur)} • ISBN ${escapeHtml(a.isbn)}</div>
                    </div>
                    <div class="intake-api-actions">
                        <button type="button" class="btn-forest-sm" onclick="addApiBookDirect('${a.isbn}', false)">⚡ Ajouter</button>
                        <button type="button" class="btn-soft-sm" onclick="addApiBookDirect('${a.isbn}', true)" title="Générer un code interne CAMUS-xxxx pour étiquette">🏷️ Sans code</button>
                        <button type="button" class="btn-soft-sm" onclick="prefillManualFromApi('${a.isbn}')" title="Compléter ou modifier avant ajout">✏️</button>
                    </div>
                </div>
            `;
        });

        if (existingMatches.length === 0 && apiMatches.length === 0) {
            html = `
                <div class="alert-inline-warn" style="display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap;">
                    <span>Aucun résultat BNF / Google Books pour « <strong>${escapeHtml(raw)}</strong> ».</span>
                    <button type="button" class="btn-forest-sm" onclick="prepareManualFromQuery()">✍️ Saisir « ${escapeHtml(raw.slice(0, 24))} » à la main</button>
                </div>
            `;
        }

        box.innerHTML = html;
    }

    function handleIntakeSubmit(e) {
        if (e) e.preventDefault();
        const inp = document.getElementById('uniIntakeInput');
        const raw = (inp ? inp.value : INTAKE_STATE.query).trim();
        if (!raw) return;

        const clean = normalizeCode(raw);
        if (isLikelyIsbn(raw)) {
            const existing = CATALOG_BOOKS.find(b => normalizeCode(b.isbn) === clean);
            if (existing) {
                registerAddedBook(existing, '⚡ Douchette (+1 ex.)', 1, false);
                notify(`+1 exemplaire ajouté à <strong>${escapeHtml(existing.titre)}</strong>`, '⚡');
                clearOmnibarAfterScan();
                return;
            }
            const apiBook = API_SIMULATED_BOOKS.find(a => normalizeCode(a.isbn) === clean);
            if (apiBook) {
                if (INTAKE_STATE.rafale) {
                    registerAddedBook(apiBook, `⚡ Douchette + ${apiBook.source}`, 1, false);
                    notify(`<strong>${escapeHtml(apiBook.titre)}</strong> trouvé via ${apiBook.source} et ajouté !`, '⚡');
                    clearOmnibarAfterScan();
                } else {
                    prefillManualFromApi(apiBook.isbn);
                }
                return;
            }
            // ISBN inconnu des API -> bascule fluide vers la saisie manuelle
            prepareManualFromQuery(clean);
            notify(`ISBN ${clean} inconnu — complétez le titre ci-dessous`, '✍️');
            return;
        }

        // Recherche texte par Entrée : si 1 seul résultat API évident en rafale ? On laisse le choix visible ou pré-remplit si vide
        updateIntakeApiResults();
    }

    function clearOmnibarAfterScan() {
        INTAKE_STATE.query = '';
        const inp = document.getElementById('uniIntakeInput');
        if (inp) {
            inp.value = '';
            inp.focus();
        }
        updateIntakeApiResults();
        updateIntakeSessionPanel();
    }

    function addExistingCatalogCopyFromIntake(bookId) {
        const bk = CATALOG_BOOKS.find(b => b.id === bookId);
        if (!bk) return;
        registerAddedBook(bk, '⚡ +1 exemplaire', 1, false);
        updateIntakeApiResults();
        updateIntakeSessionPanel();
        notify(`+1 exemplaire ajouté à <strong>${escapeHtml(bk.titre)}</strong>`, '📦');
    }

    function addApiBookDirect(isbn, withoutBarcode) {
        const clean = normalizeCode(isbn);
        const apiBook = API_SIMULATED_BOOKS.find(a => normalizeCode(a.isbn) === clean);
        if (!apiBook) return;
        const tag = withoutBarcode ? `🏷️ Sans code (${apiBook.source})` : `🔍 API ${apiBook.source}`;
        const book = registerAddedBook(apiBook, tag, 1, !!withoutBarcode);
        updateIntakeApiResults();
        updateIntakeSessionPanel();
        notify(
            withoutBarcode
                ? `<strong>${escapeHtml(book.titre)}</strong> ajouté avec code <strong>${book.isbn}</strong> (étiquette prête)`
                : `<strong>${escapeHtml(book.titre)}</strong> ajouté au catalogue`,
            withoutBarcode ? '🏷️' : '✅'
        );
    }

    function prefillManualFromApi(isbn) {
        const clean = normalizeCode(isbn);
        const apiBook = API_SIMULATED_BOOKS.find(a => normalizeCode(a.isbn) === clean);
        if (!apiBook) return;
        const t = document.getElementById('uniManTitre');
        const a = document.getElementById('uniManAuteur');
        const i = document.getElementById('uniManIsbn');
        const g = document.getElementById('uniManGenre');
        const z = document.getElementById('uniManZone');
        const st = document.getElementById('intakeManualStatus');
        if (t) t.value = apiBook.titre;
        if (a) a.value = apiBook.auteur;
        if (i) i.value = apiBook.isbn;
        if (g) g.value = apiBook.genre;
        if (z) z.value = apiBook.zoneKey;
        if (st) st.innerText = `Pré-rempli depuis ${apiBook.source}`;
        document.getElementById('intakeManualWrap')?.classList.add('highlight-manual');
        if (t) t.focus();
    }

    function prepareManualFromQuery(forcedIsbn) {
        const raw = INTAKE_STATE.query.trim();
        const t = document.getElementById('uniManTitre');
        const i = document.getElementById('uniManIsbn');
        const st = document.getElementById('intakeManualStatus');
        if (forcedIsbn) {
            if (i) i.value = forcedIsbn;
            if (t) { t.value = ''; t.focus(); }
        } else {
            if (t && raw) t.value = raw;
            if (i && !i.value) toggleIntakeNoBarcode(true);
            if (t) t.focus();
        }
        if (st) st.innerText = 'Complétez puis validez';
        document.getElementById('intakeManualWrap')?.classList.add('highlight-manual');
    }

    function toggleIntakeNoBarcode(checked) {
        INTAKE_STATE.noBarcode = !!checked;
        const chk = document.getElementById('uniNoBarcodeChk');
        const tag = document.getElementById('uniNoBarcodeTag');
        const isbnInp = document.getElementById('uniManIsbn');
        if (chk) chk.checked = INTAKE_STATE.noBarcode;
        if (tag) tag.innerText = INTAKE_STATE.noBarcode ? 'Étiquette auto' : 'Optionnel';
        if (isbnInp) {
            if (INTAKE_STATE.noBarcode && !isbnInp.value.startsWith('CAMUS-')) {
                isbnInp.value = nextCamusCode();
            } else if (!INTAKE_STATE.noBarcode && isbnInp.value.startsWith('CAMUS-')) {
                isbnInp.value = '';
            }
        }
    }

    function clearUnifiedManualForm() {
        ['uniManTitre', 'uniManAuteur', 'uniManIsbn'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
        const q = document.getElementById('uniManQty');
        if (q) q.value = '1';
        toggleIntakeNoBarcode(false);
        document.getElementById('intakeManualWrap')?.classList.remove('highlight-manual');
        const st = document.getElementById('intakeManualStatus');
        if (st) st.innerText = 'Prêt';
    }

    function submitUnifiedManualBook() {
        const titre = (document.getElementById('uniManTitre')?.value || '').trim();
        if (!titre) {
            notify('Indiquez au moins le titre du livre', '⚠️');
            document.getElementById('uniManTitre')?.focus();
            return;
        }
        const auteur = (document.getElementById('uniManAuteur')?.value || '').trim() || 'Auteur jeunesse';
        const isbnVal = (document.getElementById('uniManIsbn')?.value || '').trim();
        const genre = document.getElementById('uniManGenre')?.value || 'Album';
        const zoneSel = document.getElementById('uniManZone');
        const zoneKey = zoneSel ? zoneSel.value : '6_haut';
        const zoneLabel = zoneSel ? zoneSel.options[zoneSel.selectedIndex].text : 'Îlot 6';
        const qty = Math.max(1, parseInt(document.getElementById('uniManQty')?.value || '1', 10));
        const noBarcode = INTAKE_STATE.noBarcode || !isbnVal || isbnVal.startsWith('CAMUS-');

        registerAddedBook(
            { titre, auteur, isbn: isbnVal, genre, zoneKey, zoneLabel },
            noBarcode ? '🏷️ Manuel sans code' : '✍️ Saisie manuelle',
            qty,
            noBarcode
        );
        clearUnifiedManualForm();
        updateIntakeApiResults();
        updateIntakeSessionPanel();
        notify(`<strong>${escapeHtml(titre)}</strong> ajouté (${qty} ex.)`, '✅');
    }

    function intakeDemo(kind) {
        const inp = document.getElementById('uniIntakeInput');
        if (kind === 'isbn_known') {
            INTAKE_STATE.query = '9782070612802';
            if (inp) inp.value = INTAKE_STATE.query;
            handleIntakeSubmit();
        } else if (kind === 'isbn_new') {
            INTAKE_STATE.query = '9782070518425';
            if (inp) inp.value = INTAKE_STATE.query;
            handleIntakeSubmit();
        } else if (kind === 'api_title') {
            INTAKE_STATE.query = 'Loup';
            if (inp) { inp.value = 'Loup'; inp.focus(); }
            updateIntakeApiResults();
        } else if (kind === 'no_barcode') {
            INTAKE_STATE.query = 'Babar';
            if (inp) { inp.value = 'Babar'; inp.focus(); }
            updateIntakeApiResults();
        } else if (kind === 'manual_unknown') {
            INTAKE_STATE.query = 'Recueil de poèmes CM1';
            if (inp) inp.value = INTAKE_STATE.query;
            updateIntakeApiResults();
            prepareManualFromQuery();
        }
    }

    function updateIntakeSessionPanel() {
        const head = document.getElementById('intakeSessionHeader');
        const list = document.getElementById('intakeSessionList');
        const btn = document.getElementById('btnSessionEtiquettes');
        const items = INTAKE_STATE.sessionItems;
        const totalCopies = items.reduce((s, i) => s + i.exIds.length, 0);

        if (head) head.innerHTML = `<h4>📦 Pile ajoutée cette session (${totalCopies} ex.)</h4>`;
        if (btn) btn.innerHTML = `🏷️ Imprimer les étiquettes de cette session (${items.length} titre${items.length > 1 ? 's' : ''})`;
        if (!list) return;

        if (items.length === 0) {
            list.innerHTML = `
                <div class="empty-inline-box" style="margin-top:8px;">
                    <span>📥</span>
                    <div>
                        <strong>Session prête</strong>
                        <p class="muted-xs">Vos ajouts (douchette, API ou manuel) apparaîtront ici avec leur n° d'exemplaire et leur rayon.</p>
                    </div>
                </div>
            `;
            return;
        }

        list.innerHTML = items.map(it => `
            <div class="intake-session-item">
                <div class="book-cover" style="background:${it.gradient}; width:30px; height:40px; font-size:14px;">${it.emoji}</div>
                <div style="flex:1; min-width:0;">
                    <div style="font-weight:800; font-size:12px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(it.titre)}</div>
                    <div style="display:flex; gap:5px; align-items:center; flex-wrap:wrap; margin-top:2px;">
                        <span class="tag-pill ${it.noBarcode ? 'gold' : 'soft'}">${escapeHtml(it.sourceTag)}</span>
                        <span class="mono-badge">${escapeHtml(it.exIds.join(', '))}</span>
                    </div>
                    <div class="muted-xs" style="margin-top:2px;">📍 ${escapeHtml(it.zoneLabel)}</div>
                </div>
                <button type="button" class="intake-remove-btn" onclick="removeSessionIntakeItem('${it.sessionId}')" title="Retirer de la liste">✕</button>
            </div>
        `).join('');
    }

    function removeSessionIntakeItem(sessionId) {
        const idx = INTAKE_STATE.sessionItems.findIndex(i => i.sessionId === sessionId);
        if (idx === -1) return;
        const item = INTAKE_STATE.sessionItems[idx];
        const bk = CATALOG_BOOKS.find(b => b.id === item.bookId);
        if (bk) {
            bk.exemplaires = bk.exemplaires.filter(ex => !item.exIds.includes(ex.id));
        }
        INTAKE_STATE.sessionItems.splice(idx, 1);
        rebuildRecolementItems();
        renderLivresView();
        updateIntakeSessionPanel();
        updateIntakeApiResults();
        notify('Ajout retiré de la session', '↩️');
    }

    function sendSessionToEtiquettes() {
        const items = INTAKE_STATE.sessionItems;
        if (items.length === 0) {
            closeModal();
            GESTION_STATE.subTab = 'etiquettes';
            switchMainView('gestion');
            return;
        }
        items.forEach(it => {
            const prev = ETIQUETTES_STATE.selected[it.bookId];
            ETIQUETTES_STATE.selected[it.bookId] = {
                checked: true,
                qty: prev ? Math.max(prev.qty, it.exIds.length) : it.exIds.length
            };
            ETIQUETTES_STATE.focusedBookId = it.bookId;
        });
        closeModal();
        GESTION_STATE.subTab = 'etiquettes';
        switchMainView('gestion');
        chime();
        notify(`${items.length} titre${items.length > 1 ? 's' : ''} préparé${items.length > 1 ? 's' : ''} pour impression d'étiquettes`, '🏷️');
    }

    function openImportLivresCsvModal() {
        openModal(
            '📥 Importer un fichier CSV de livres',
            `<div class="form-stack">
                <p class="muted-xs">Formats acceptés : <code>isbn;titre;auteur;quantite</code> ou export de tableur Excel (séparateur <code>;</code> ou <code>,</code>).</p>
                <div class="alert-inline-ok">✅ Simulation prête : 18 nouveaux ouvrages détectés sans doublon dans <code>rentree_lot_cm1.csv</code>.</div>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Fermer</button>
             <button class="btn-forest" onclick="closeModal(); chime(); notify('Import CSV simulé : lot validé sans erreur', '📥')">Lancer l'import</button>`
        );
    }

    // ------------------------------------------------------------------------
    // 7. VUE 🎒 MALLETTES PÉDAGOGIQUES
    // ------------------------------------------------------------------------
    function getFilteredMallettes() {
        const q = MALLETTES_STATE.search.trim().toLowerCase();
        return MALLETTES_DATA.filter(m => {
            if (MALLETTES_STATE.statut !== 'ALL' && m.statut !== MALLETTES_STATE.statut) return false;
            if (MALLETTES_STATE.theme !== 'ALL' && m.theme !== MALLETTES_STATE.theme) return false;
            if (q) {
                const hay = `${m.titre} ${m.code} ${m.theme} ${m.niveau} ${m.emprunteur || ''}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });
    }

    function renderMallettesView() {
        const root = document.getElementById('view-mallettes');
        if (!root) return;

        const themes = [...new Set(MALLETTES_DATA.map(m => m.theme))].sort();
        const filtered = getFilteredMallettes();
        const selected = MALLETTES_DATA.find(m => m.id === MALLETTES_STATE.selectedId) || filtered[0] || null;
        if (selected) MALLETTES_STATE.selectedId = selected.id;

        const totalM = MALLETTES_DATA.length;
        const dispoM = MALLETTES_DATA.filter(m => m.statut === 'DISPONIBLE').length;
        const pretM = MALLETTES_DATA.filter(m => m.statut === 'EN_PRET').length;
        const totalBooksInM = MALLETTES_DATA.reduce((s, m) => s + m.items.reduce((a, i) => a + i.qty, 0), 0);

        const cardsHtml = filtered.length === 0
            ? `<div class="empty-state-card"><strong>Aucune mallette trouvée</strong></div>`
            : filtered.map(m => {
                const isSel = selected && selected.id === m.id;
                const nbBooks = m.items.reduce((a, i) => a + i.qty, 0);
                return `
                    <div class="mallette-list-card ${isSel ? 'selected' : ''}" style="border-left: 5px solid ${m.accentColor};" onclick="selectMallette('${m.id}')">
                        <div style="display:flex; justify-content:space-between; align-items:center; gap:8px;">
                            <span class="mono-badge">${escapeHtml(m.code)}</span>
                            <span class="status-pill ${m.statut === 'DISPONIBLE' ? 'pill-forest' : 'pill-gold'}">
                                ${m.statut === 'DISPONIBLE' ? 'Disponible' : 'En prêt'}
                            </span>
                        </div>
                        <div class="mallette-card-title">${escapeHtml(m.titre)}</div>
                        <div class="muted-xs">${escapeHtml(m.theme)} • ${escapeHtml(m.niveau)} • ${nbBooks} ouvrage${nbBooks > 1 ? 's' : ''}</div>
                        ${m.emprunteur ? `<div class="mallette-borrower-line">👤 ${escapeHtml(m.emprunteur)}</div>` : ''}
                    </div>
                `;
            }).join('');

        let detailHtml = `<div class="empty-state-card"><span style="font-size:40px;">🎒</span><strong>Sélectionnez une mallette</strong></div>`;
        if (selected) {
            const nbBooks = selected.items.reduce((a, i) => a + i.qty, 0);
            let subContentHtml = '';

            if (MALLETTES_STATE.subTab === 'contenu') {
                subContentHtml = `
                    ${selected.statut === 'EN_PRET'
                        ? `<div class="alert-inline-info">📦 Actuellement empruntée par <strong>${escapeHtml(selected.emprunteur)}</strong> (retour prévu le ${escapeHtml(selected.dateRetour)}).</div>`
                        : ''}
                    <div class="toolbar-row" style="gap:8px;">
                        <input id="malletteAddBookInput" type="text" placeholder="🔍 Ajouter un livre par titre ou cote (ex: Petit Prince)…" style="flex:1;">
                        <button class="btn-forest-sm" onclick="addBookToMallette('${selected.id}')">➕ Ajouter</button>
                        <button class="btn-soft-sm" onclick="fillMalletteByTheme('${selected.id}')">🪄 Remplir par thème</button>
                    </div>
                    <div class="mallette-books-grid">
                        ${selected.items.length === 0
                            ? `<p class="muted-xs">Cette mallette est encore vide. Utilisez le bouton « Remplir par thème » ou ajoutez un titre ci-dessus.</p>`
                            : selected.items.map((item, idx) => {
                                const bk = CATALOG_BOOKS.find(b => b.id === item.bookId) || CATALOG_BOOKS[0];
                                return `
                                    <div class="mallette-book-tile">
                                        <div class="book-cover" style="background:${bk.gradient};">${bk.emoji}</div>
                                        <div style="flex:1; min-width:0;">
                                            <div class="book-title">${escapeHtml(bk.titre)}</div>
                                            <div class="muted-xs">${escapeHtml(bk.auteur)} • ${escapeHtml(item.exId)}</div>
                                        </div>
                                        <div class="stepper-box">
                                            <button onclick="stepMalletteItem('${selected.id}', ${idx}, -1)">−</button>
                                            <span>${item.qty}</span>
                                            <button onclick="stepMalletteItem('${selected.id}', ${idx}, 1)">+</button>
                                        </div>
                                    </div>
                                `;
                            }).join('')}
                    </div>
                `;
            } else if (MALLETTES_STATE.subTab === 'ressources') {
                subContentHtml = `
                    <div class="detail-section-card">
                        <div class="section-row-head">
                            <h4>📚 Fiches & documents de la mallette</h4>
                            <button class="btn-forest-sm" onclick="addMalletteResource('${selected.id}')">➕ Ajouter une fiche</button>
                        </div>
                        <ul class="resource-list">
                            ${selected.ressources.map(r => `<li>📄 <strong>${escapeHtml(r.titre)}</strong> <span class="tag-pill soft">${escapeHtml(r.type)}</span></li>`).join('')}
                        </ul>
                    </div>
                `;
            } else if (MALLETTES_STATE.subTab === 'historique') {
                subContentHtml = `
                    <div class="detail-section-card">
                        <h4>📜 Journal des mouvements de caisse</h4>
                        <div class="copies-list" style="margin-top:8px;">
                            ${selected.historique.map(h => `
                                <div class="copy-row">
                                    <span class="mono-badge">${escapeHtml(h.date)}</span>
                                    <span style="flex:1;">${escapeHtml(h.action)}</span>
                                    <span class="muted-xs">${escapeHtml(h.par)}</span>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
            } else {
                subContentHtml = `
                    <div class="detail-section-card">
                        <h4>ℹ️ Description & usage pédagogique</h4>
                        <p style="font-size:12.5px; line-height:1.5; margin-top:6px;">${escapeHtml(selected.description)}</p>
                        <div class="catalog-tags-row" style="margin-top:10px;">
                            <span class="tag-pill">${escapeHtml(selected.theme)}</span>
                            <span class="tag-pill soft">${escapeHtml(selected.niveau)}</span>
                            <span class="tag-pill gold">${nbBooks} ouvrage${nbBooks > 1 ? 's' : ''}</span>
                        </div>
                    </div>
                `;
            }

            detailHtml = `
                <div class="detail-panel-inner">
                    <div class="mallette-detail-head">
                        <div>
                            <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                                <span class="mono-badge">${escapeHtml(selected.code)}</span>
                                <h2 class="detail-book-title">${escapeHtml(selected.titre)}</h2>
                                <span class="status-pill ${selected.statut === 'DISPONIBLE' ? 'pill-forest' : 'pill-gold'}">
                                    ${selected.statut === 'DISPONIBLE' ? 'Disponible' : 'En prêt'}
                                </span>
                            </div>
                            <div class="catalog-tags-row" style="margin-top:6px;">
                                <span class="tag-pill">${escapeHtml(selected.theme)}</span>
                                <span class="tag-pill soft">${escapeHtml(selected.niveau)}</span>
                                <span class="tag-pill gold">${nbBooks} livre${nbBooks > 1 ? 's' : ''}</span>
                            </div>
                        </div>
                        <div class="header-actions">
                            ${selected.statut === 'DISPONIBLE'
                                ? `<button class="btn-forest" onclick="openLoanMalletteModal('${selected.id}')">📤 Prêter à une classe</button>`
                                : `<button class="btn-blue" onclick="returnMallette('${selected.id}')">↩ Enregistrer le retour</button>`}
                            <button class="btn-soft" onclick="openMallettePrintPreview('${selected.id}')">🖨️ Fiche A4</button>
                            <button class="btn-soft" onclick="notify('Étiquette caisse ${escapeHtml(selected.code)} prête pour impression', '🏷️')">🏷️ Caisse</button>
                        </div>
                    </div>

                    <div class="sub-tabs">
                        <button class="sub-tab-btn ${MALLETTES_STATE.subTab === 'contenu' ? 'active' : ''}" onclick="setMalletteSubTab('contenu')">📦 Contenu (${nbBooks})</button>
                        <button class="sub-tab-btn ${MALLETTES_STATE.subTab === 'ressources' ? 'active' : ''}" onclick="setMalletteSubTab('ressources')">📚 Ressources (${selected.ressources.length})</button>
                        <button class="sub-tab-btn ${MALLETTES_STATE.subTab === 'historique' ? 'active' : ''}" onclick="setMalletteSubTab('historique')">📜 Historique</button>
                        <button class="sub-tab-btn ${MALLETTES_STATE.subTab === 'infos' ? 'active' : ''}" onclick="setMalletteSubTab('infos')">ℹ️ Infos</button>
                    </div>

                    <div style="flex:1; display:flex; flex-direction:column; gap:10px;">
                        ${subContentHtml}
                    </div>
                </div>
            `;
        }

        root.innerHTML = `
            <div class="subpage-header">
                <div>
                    <h2>🎒 Mallettes Pédagogiques</h2>
                    <p>Constituez des caisses thématiques pour les classes et prêtez-les en un clic</p>
                </div>
                <div class="header-actions">
                    <div class="kpi-pills-row">
                        <span class="kpi-pill-mini">${totalM} mallettes</span>
                        <span class="kpi-pill-mini green">${dispoM} disponibles</span>
                        <span class="kpi-pill-mini gold">${pretM} en prêt</span>
                        <span class="kpi-pill-mini">${totalBooksInM} ouvrages</span>
                    </div>
                    <button class="btn-forest" onclick="openCreateMalletteModal()">➕ Nouvelle Mallette</button>
                </div>
            </div>

            <div class="toolbar-card">
                <div class="toolbar-filters">
                    <div class="search-wrap" style="flex:1;">
                        <span>🔍</span>
                        <input id="malletteSearchInput" type="text" value="${escapeHtml(MALLETTES_STATE.search)}"
                            placeholder="Rechercher par titre, thème, code (ex: Harcèlement, MAL-001)…"
                            oninput="onMalletteSearch(this.value)">
                    </div>
                    <label>Statut :
                        <select onchange="MALLETTES_STATE.statut=this.value; renderMallettesView();">
                            <option value="ALL">Tous</option>
                            <option value="DISPONIBLE" ${MALLETTES_STATE.statut === 'DISPONIBLE' ? 'selected' : ''}>Disponibles</option>
                            <option value="EN_PRET" ${MALLETTES_STATE.statut === 'EN_PRET' ? 'selected' : ''}>En prêt</option>
                        </select>
                    </label>
                    <label>Thème :
                        <select onchange="MALLETTES_STATE.theme=this.value; renderMallettesView();">
                            <option value="ALL">Tous</option>
                            ${themes.map(t => `<option value="${escapeHtml(t)}" ${MALLETTES_STATE.theme === t ? 'selected' : ''}>${escapeHtml(t)}</option>`).join('')}
                        </select>
                    </label>
                </div>
            </div>

            <div class="split-layout split-35-65">
                <div class="split-left panel-scroll" style="display:flex; flex-direction:column; gap:10px;">${cardsHtml}</div>
                <div class="split-right detail-panel panel-scroll">${detailHtml}</div>
            </div>
        `;
    }

    function onMalletteSearch(val) {
        MALLETTES_STATE.search = val;
        const pos = document.getElementById('malletteSearchInput')?.selectionStart || val.length;
        renderMallettesView();
        const inp = document.getElementById('malletteSearchInput');
        if (inp) {
            inp.focus();
            inp.setSelectionRange(pos, pos);
        }
    }

    function selectMallette(id) {
        MALLETTES_STATE.selectedId = id;
        renderMallettesView();
    }

    function setMalletteSubTab(tab) {
        MALLETTES_STATE.subTab = tab;
        renderMallettesView();
    }

    function stepMalletteItem(malId, idx, delta) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        if (!mal || !mal.items[idx]) return;
        mal.items[idx].qty += delta;
        if (mal.items[idx].qty <= 0) mal.items.splice(idx, 1);
        renderMallettesView();
    }

    function addBookToMallette(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        const q = (document.getElementById('malletteAddBookInput')?.value || '').trim().toLowerCase();
        if (!mal) return;
        const match = CATALOG_BOOKS.find(b => !q || b.titre.toLowerCase().includes(q) || b.cote.toLowerCase().includes(q)) || CATALOG_BOOKS[0];
        const existing = mal.items.find(i => i.bookId === match.id);
        if (existing) existing.qty += 1;
        else mal.items.push({ bookId: match.id, exId: match.exemplaires[0]?.id || '#--', qty: 1 });
        chime();
        renderMallettesView();
        notify(`« ${escapeHtml(match.titre)} » ajouté à ${escapeHtml(mal.code)}`, '📦');
    }

    function fillMalletteByTheme(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        if (!mal) return;
        let added = 0;
        CATALOG_BOOKS.forEach(b => {
            if (added < 2 && !mal.items.some(i => i.bookId === b.id)) {
                mal.items.push({ bookId: b.id, exId: b.exemplaires[0]?.id || '#--', qty: 1 });
                added++;
            }
        });
        chime();
        renderMallettesView();
        notify(`Complétée automatiquement (+${added} ouvrage${added > 1 ? 's' : ''})`, '🪄');
    }

    function addMalletteResource(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        if (!mal) return;
        mal.ressources.push({ titre: 'Fiche d’exploitation classe (ajout rapide)', type: 'Document BCD' });
        chime();
        renderMallettesView();
        notify('Nouvelle fiche ajoutée à la mallette', '📚');
    }

    function openLoanMalletteModal(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        if (!mal) return;
        openModal(
            `📤 Prêter la mallette ${escapeHtml(mal.code)}`,
            `<div class="form-stack">
                <p><strong>${escapeHtml(mal.titre)}</strong></p>
                <label>Classe / Enseignant destinataire
                    <select id="loanMalletteClass">
                        <option>Mme Roy — Classe CP</option>
                        <option>Mme Blanc — Classe CE1</option>
                        <option>M. Martin — Classe CE2</option>
                        <option>Mme Dupont — Classe CM1-A</option>
                        <option>M. Simon — Classe CM2-B</option>
                    </select>
                </label>
                <label>Date de retour souhaitée
                    <input id="loanMalletteDate" type="text" value="18 décembre 2026">
                </label>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Annuler</button>
             <button class="btn-forest" onclick="confirmLoanMallette('${mal.id}')">✓ Valider le prêt</button>`
        );
    }

    function confirmLoanMallette(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        const cls = document.getElementById('loanMalletteClass')?.value || 'Classe CM1-A';
        const dt = document.getElementById('loanMalletteDate')?.value || '18 décembre 2026';
        if (!mal) return;
        mal.statut = 'EN_PRET';
        mal.emprunteur = cls;
        mal.dateRetour = dt;
        mal.historique.unshift({ date: "Aujourd'hui", action: `Prêtée à ${cls}`, par: 'Le Comptoir' });
        closeModal();
        chime();
        renderMallettesView();
        notify(`Mallette <strong>${escapeHtml(mal.code)}</strong> prêtée à ${escapeHtml(cls)}`, '📤');
    }

    function returnMallette(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        if (!mal) return;
        const prev = mal.emprunteur;
        mal.historique.unshift({ date: "Aujourd'hui", action: `Retour complet enregistré (${prev})`, par: 'Le Comptoir' });
        mal.statut = 'DISPONIBLE';
        mal.emprunteur = null;
        mal.dateRetour = null;
        chime();
        renderMallettesView();
        notify(`Retour de la mallette <strong>${escapeHtml(mal.code)}</strong> enregistré`, '✅');
    }

    function openMallettePrintPreview(malId) {
        const mal = MALLETTES_DATA.find(m => m.id === malId);
        if (!mal) return;
        openModal(
            `🖨️ Fiche récapitulative A4 — ${escapeHtml(mal.code)}`,
            `<div class="a4-preview-sheet">
                <div style="display:flex; justify-content:space-between; border-bottom:2px solid #1E5139; padding-bottom:8px; margin-bottom:10px;">
                    <div><strong>ÉCOLE ALBERT CAMUS PASTEUR • MEUDON</strong><br><span class="muted-xs">Fiche de caisse pédagogique BCD Express</span></div>
                    <span class="mono-badge">${escapeHtml(mal.code)}</span>
                </div>
                <h3 style="margin-bottom:4px;">${escapeHtml(mal.titre)}</h3>
                <p class="muted-xs" style="margin-bottom:10px;">Thème : ${escapeHtml(mal.theme)} • Niveau : ${escapeHtml(mal.niveau)}</p>
                <div class="copies-list">
                    ${mal.items.map(it => {
                        const b = CATALOG_BOOKS.find(x => x.id === it.bookId) || CATALOG_BOOKS[0];
                        return `<div class="copy-row"><span>☑️</span><strong>${escapeHtml(b.titre)}</strong><span class="muted-xs" style="margin-left:auto;">${escapeHtml(it.exId)} (×${it.qty})</span></div>`;
                    }).join('')}
                </div>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Fermer</button>
             <button class="btn-forest" onclick="closeModal(); chime(); notify('Impression A4 lancée', '🖨️')">🖨️ Imprimer la fiche</button>`
        );
    }

    function openCreateMalletteModal() {
        const nextCode = `MAL-00${MALLETTES_DATA.length + 1}`;
        openModal(
            '➕ Nouvelle Mallette Pédagogique',
            `<div class="form-stack">
                <label>Titre de la mallette
                    <input id="newMalTitre" type="text" placeholder="Ex: Mythologie grecque & Héros">
                </label>
                <div class="inline-form-row">
                    <label style="flex:1;">Thème
                        <input id="newMalTheme" type="text" value="Histoire & Culture">
                    </label>
                    <label style="flex:1;">Niveau ciblé
                        <select id="newMalNiveau">
                            <option>Cycle 3 (CM1-CM2)</option>
                            <option>Cycle 2 (CP-CE2)</option>
                            <option>Cycle 2 & 3</option>
                        </select>
                    </label>
                </div>
                <label>Description courte
                    <input id="newMalDesc" type="text" value="Caisse thématique préparée pour le prêt aux classes.">
                </label>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Annuler</button>
             <button class="btn-forest" onclick="confirmCreateMallette('${nextCode}')">✓ Créer ${nextCode}</button>`
        );
    }

    function confirmCreateMallette(code) {
        const titre = (document.getElementById('newMalTitre')?.value || '').trim() || 'Nouvelle mallette thématique';
        const theme = (document.getElementById('newMalTheme')?.value || '').trim() || 'Thème libre';
        const niveau = document.getElementById('newMalNiveau')?.value || 'Cycle 2 & 3';
        const desc = (document.getElementById('newMalDesc')?.value || '').trim();
        MALLETTES_DATA.unshift({
            id: code,
            code,
            titre,
            theme,
            niveau,
            statut: 'DISPONIBLE',
            emprunteur: null,
            dateRetour: null,
            accentColor: '#2A5C8A',
            description: desc,
            items: [{ bookId: CATALOG_BOOKS[0].id, exId: '#101', qty: 1 }],
            ressources: [{ titre: 'Fiche sommaire de la mallette', type: 'Document BCD' }],
            historique: [{ date: "Aujourd'hui", action: 'Création de la mallette', par: 'Admin BCD' }]
        });
        MALLETTES_STATE.selectedId = code;
        closeModal();
        chime();
        renderMallettesView();
        notify(`Mallette <strong>${escapeHtml(code)}</strong> créée`, '🎒');
    }

    // ------------------------------------------------------------------------
    // 8. VUE ⚙️ GESTION (ADMIN) / 👧 MA CLASSE (ENSEIGNANT)
    // ------------------------------------------------------------------------
    function renderGestionView() {
        const root = document.getElementById('view-gestion');
        if (!root) return;

        const isTeacher = SESSION_STATE.role === 'TEACHER';
        const profile = getActiveProfile();
        const sub = isTeacher ? 'eleves' : GESTION_STATE.subTab;
        let bodyHtml = '';

        if (sub === 'eleves') bodyHtml = buildGestionElevesHtml();
        else if (sub === 'etiquettes') bodyHtml = buildGestionEtiquettesHtml();
        else if (sub === 'recolement') bodyHtml = buildGestionRecolementHtml();
        else bodyHtml = buildGestionOutilsHtml();

        const headerHtml = isTeacher
            ? `
                <div>
                    <h2>👧 Ma Classe — ${escapeHtml(SESSION_STATE.classe)} (${escapeHtml(profile.teacherName)})</h2>
                    <p>Vue filtrée sur vos élèves uniquement : prêts en cours, retards et coupons cahier de liaison</p>
                </div>
                <div class="header-actions">
                    <button class="btn-forest" onclick="openClassReminderCouponsModal('${escapeHtml(SESSION_STATE.classe)}')">🖨️ Coupons cahier de liaison</button>
                    <button class="btn-soft" onclick="openProfilePicker()">🔄 Changer de profil</button>
                </div>
            `
            : `
                <div>
                    <h2>⚙️ Administration & Gestion de la BCD</h2>
                    <p>Utilisateurs, planches d'étiquettes A4, campagnes de récolement et sauvegardes locales</p>
                </div>
                <div class="gestion-subnav">
                    <button class="tab-btn ${sub === 'eleves' ? 'active' : ''}" onclick="switchGestionTab('eleves')">👥 Élèves & Classes</button>
                    <button class="tab-btn ${sub === 'etiquettes' ? 'active' : ''}" onclick="switchGestionTab('etiquettes')">🏷️ Étiquettes A4</button>
                    <button class="tab-btn ${sub === 'recolement' ? 'active' : ''}" onclick="switchGestionTab('recolement')">📋 Récolement</button>
                    <button class="tab-btn ${sub === 'outils' ? 'active' : ''}" onclick="switchGestionTab('outils')">💾 Sauvegardes & Outils</button>
                </div>
            `;

        root.innerHTML = `
            <div class="subpage-header">
                ${headerHtml}
            </div>
            <div class="gestion-content-wrap">
                ${bodyHtml}
            </div>
        `;
    }

    function switchGestionTab(tab) {
        if (SESSION_STATE.role === 'TEACHER' && tab !== 'eleves') {
            GESTION_STATE.subTab = 'eleves';
            renderGestionView();
            return;
        }
        GESTION_STATE.subTab = tab;
        renderGestionView();
    }

    // --- 8A. GESTION > ÉLÈVES & CLASSES (FILTRAGE STRICT EN MODE ENSEIGNANT) ---
    function buildGestionElevesHtml() {
        const isTeacher = SESSION_STATE.role === 'TEACHER';
        const q = GESTION_STATE.userSearch.trim().toLowerCase();
        const classes = [...new Set(USERS_DATA.map(u => u.classe))];

        const scopeUsers = isTeacher
            ? USERS_DATA.filter(u => u.type === 'ELEVE' && u.classe === SESSION_STATE.classe)
            : USERS_DATA;

        const filtered = scopeUsers.filter(u => {
            if (!isTeacher && GESTION_STATE.userClasse !== 'ALL' && u.classe !== GESTION_STATE.userClasse) return false;
            if (q) {
                const hay = `${u.prenom} ${u.nom} ${u.numero} ${u.classe}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });

        const selected = scopeUsers.find(u => u.id === GESTION_STATE.selectedUserId) || filtered[0] || null;
        if (selected) GESTION_STATE.selectedUserId = selected.id;

        const nbEleves = scopeUsers.filter(u => u.type === 'ELEVE').length;
        const nbLoans = scopeUsers.reduce((s, u) => s + u.prets.length, 0);
        const nbLate = scopeUsers.reduce((s, u) => s + u.prets.filter(p => p.retard).length, 0);
        const nbEns = USERS_DATA.filter(u => u.type === 'ENSEIGNANT').length;

        const rowsHtml = filtered.length === 0
            ? `<div class="empty-state-card"><strong>Aucun élève trouvé</strong></div>`
            : filtered.map(u => {
                const isSel = selected && selected.id === u.id;
                const hasLate = u.prets.some(p => p.retard);
                return `
                    <div class="user-row-card ${isSel ? 'selected' : ''}" onclick="selectGestionUser('${u.id}')">
                        <div class="student-avatar">${u.avatar}</div>
                        <div style="flex:1; min-width:0;">
                            <div style="font-weight:800; font-size:13px;">${escapeHtml(u.prenom)} ${escapeHtml(u.nom)}</div>
                            <div class="muted-xs">${escapeHtml(u.numero)} • ${u.type === 'ENSEIGNANT' ? 'Enseignant' : 'Élève'} • ${escapeHtml(u.dernierPassage)}</div>
                        </div>
                        <span class="class-tag">${escapeHtml(u.classe)}</span>
                        <span class="status-pill ${hasLate ? 'pill-terracotta' : (u.prets.length > 0 ? 'pill-gold' : 'pill-forest')}">
                            ${hasLate ? '⏰ Retard' : `${u.prets.length} prêt${u.prets.length > 1 ? 's' : ''}`}
                        </span>
                    </div>
                `;
            }).join('');

        const detailHtml = !selected
            ? `<div class="empty-state-card"><strong>Sélectionnez un lecteur</strong></div>`
            : `
                <div class="detail-panel-inner">
                    <div style="display:flex; align-items:center; gap:12px;">
                        <div class="student-avatar" style="width:48px; height:48px; font-size:22px;">${selected.avatar}</div>
                        <div style="flex:1;">
                            <h3 style="font-size:16px; font-weight:800;">${escapeHtml(selected.prenom)} ${escapeHtml(selected.nom)}</h3>
                            <p class="muted-xs">${escapeHtml(selected.classe)} • Carte ${escapeHtml(selected.numero)}</p>
                        </div>
                    </div>
                    <button class="btn-forest" style="width:100%; justify-content:center;" onclick="openUserAtComptoir('${selected.id}')">
                        🖥️ Ouvrir son sac au Comptoir
                    </button>
                    <div class="detail-section-card">
                        <h4>Informations lecteur</h4>
                        <div class="kv-row"><span>Identifiant</span><strong class="mono-badge">${escapeHtml(selected.numero)}</strong></div>
                        <div class="kv-row"><span>Profil</span><strong>${selected.type === 'ENSEIGNANT' ? 'Enseignant' : 'Élève'}</strong></div>
                        <div class="kv-row"><span>Quota autorisé</span><strong>${selected.quotaMax} livre${selected.quotaMax > 1 ? 's' : ''}</strong></div>
                        <div class="kv-row"><span>Dernier passage</span><strong>${escapeHtml(selected.dernierPassage)}</strong></div>
                    </div>
                    <div class="detail-section-card">
                        <h4>Emprunts en cours (${selected.prets.length})</h4>
                        ${selected.prets.length === 0
                            ? `<p class="muted-xs">Aucun livre emprunté actuellement.</p>`
                            : `<div class="copies-list">${selected.prets.map(p => `
                                <div class="copy-row">
                                    <span>${p.emoji}</span>
                                    <span style="flex:1; font-weight:700;">${escapeHtml(p.titre)}</span>
                                    <span class="status-pill ${p.retard ? 'pill-terracotta' : 'pill-forest'}">${escapeHtml(p.echeance)}</span>
                                </div>
                            `).join('')}</div>`
                        }
                    </div>
                </div>
            `;

        const kpisHtml = isTeacher
            ? `
                <div class="kpi-grid-3">
                    <div class="kpi-card-warm"><span>👧</span><div><div class="muted-xs">Élèves de ${escapeHtml(SESSION_STATE.classe)}</div><strong>${nbEleves}</strong></div></div>
                    <div class="kpi-card-warm"><span>📖</span><div><div class="muted-xs">Livres sortis dans la classe</div><strong style="color:var(--forest);">${nbLoans}</strong></div></div>
                    <div class="kpi-card-warm"><span>⏰</span><div><div class="muted-xs">Retards à rappeler</div><strong style="color:${nbLate > 0 ? 'var(--terracotta)' : 'var(--forest)'};">${nbLate}</strong></div></div>
                </div>
            `
            : `
                <div class="kpi-grid-3">
                    <div class="kpi-card-warm"><span>👧</span><div><div class="muted-xs">Élèves inscrits</div><strong>${nbEleves}</strong></div></div>
                    <div class="kpi-card-warm"><span>👩‍🏫</span><div><div class="muted-xs">Enseignants</div><strong>${nbEns}</strong></div></div>
                    <div class="kpi-card-warm"><span>🏫</span><div><div class="muted-xs">Classes actives</div><strong>${classes.length}</strong></div></div>
                </div>
            `;

        const adminToolsHtml = isTeacher
            ? `<span class="tag-pill purple">🔒 Filtre actif : ${escapeHtml(SESSION_STATE.classe)} uniquement</span>`
            : `
                <label>Classe :
                    <select onchange="GESTION_STATE.userClasse=this.value; renderGestionView();">
                        <option value="ALL">Toutes les classes</option>
                        ${classes.map(c => `<option value="${escapeHtml(c)}" ${GESTION_STATE.userClasse === c ? 'selected' : ''}>${escapeHtml(c)}</option>`).join('')}
                    </select>
                </label>
                <button class="btn-soft" onclick="openModal('🎓 Rentrée scolaire & ONDE', '<p class=muted-xs>Assistant de passage de classe en bloc (CP ➔ CE1, CE1 ➔ CE2...) et import direct du fichier CSV ONDE de l’Éducation Nationale.</p>', '<button class=btn-forest onclick=closeModal()>Compris</button>')">🎓 Rentrée ONDE</button>
                <button class="btn-soft" onclick="notify('Import CSV élèves prêt (format nom;prenom;classe)', '📥')">📥 Import CSV</button>
                <button class="btn-forest" onclick="openAddUserModal()">➕ Ajouter</button>
            `;

        return `
            ${kpisHtml}

            <div class="toolbar-card">
                <div class="toolbar-filters">
                    <div class="search-wrap" style="flex:1;">
                        <span>🔍</span>
                        <input id="userSearchInput" type="text" value="${escapeHtml(GESTION_STATE.userSearch)}"
                            placeholder="${isTeacher ? `Rechercher un élève de ${escapeHtml(SESSION_STATE.classe)}…` : 'Rechercher par nom, prénom ou carte (ex: EL-042)…'}"
                            oninput="onGestionUserSearch(this.value)">
                    </div>
                    ${adminToolsHtml}
                </div>
            </div>

            <div class="split-layout split-60-40">
                <div class="split-left panel-scroll" style="display:flex; flex-direction:column; gap:8px;">${rowsHtml}</div>
                <div class="split-right detail-panel panel-scroll">${detailHtml}</div>
            </div>
        `;
    }

    function onGestionUserSearch(val) {
        GESTION_STATE.userSearch = val;
        const pos = document.getElementById('userSearchInput')?.selectionStart || val.length;
        renderGestionView();
        const inp = document.getElementById('userSearchInput');
        if (inp) {
            inp.focus();
            inp.setSelectionRange(pos, pos);
        }
    }

    function selectGestionUser(id) {
        GESTION_STATE.selectedUserId = id;
        renderGestionView();
    }

    function openAddUserModal() {
        openModal(
            '➕ Nouvel utilisateur BCD',
            `<div class="form-stack">
                <div class="inline-form-row">
                    <label style="flex:1;">Prénom<input id="nuPrenom" type="text" placeholder="Ex: Emma"></label>
                    <label style="flex:1;">Nom<input id="nuNom" type="text" placeholder="Ex: BERNARD"></label>
                </div>
                <div class="inline-form-row">
                    <label style="flex:1;">Classe
                        <select id="nuClasse">
                            <option>CP</option><option>CE1</option><option>CE2</option><option selected>CM1-A</option><option>CM2-B</option>
                        </select>
                    </label>
                    <label style="flex:1;">Profil
                        <select id="nuType">
                            <option value="ELEVE">Élève</option>
                            <option value="ENSEIGNANT">Enseignant</option>
                        </select>
                    </label>
                </div>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Annuler</button>
             <button class="btn-forest" onclick="confirmAddUser()">✓ Créer la carte</button>`
        );
    }

    function confirmAddUser() {
        const prenom = (document.getElementById('nuPrenom')?.value || '').trim() || 'Emma';
        const nom = ((document.getElementById('nuNom')?.value || '').trim() || 'BERNARD').toUpperCase();
        const classe = document.getElementById('nuClasse')?.value || 'CM1-A';
        const type = document.getElementById('nuType')?.value || 'ELEVE';
        const id = 'usr_' + (USERS_DATA.length + 1);
        const numero = type === 'ENSEIGNANT' ? `ENS-0${USERS_DATA.length}` : `EL-0${90 + USERS_DATA.length}`;

        USERS_DATA.unshift({
            id,
            numero,
            nom,
            prenom,
            classe,
            type,
            avatar: type === 'ENSEIGNANT' ? '👩‍🏫' : '👧',
            quotaMax: type === 'ENSEIGNANT' ? 10 : 3,
            dernierPassage: 'Nouvel inscrit',
            prets: []
        });
        GESTION_STATE.selectedUserId = id;
        closeModal();
        chime();
        renderGestionView();
        notify(`Carte <strong>${numero}</strong> créée pour ${escapeHtml(prenom)} ${escapeHtml(nom)}`, '👥');
    }

    // --- 8B. GESTION > ÉTIQUETTES A4 ---
    function getEtiquettesTotals() {
        let booksCount = 0;
        let labelsCount = 0;
        Object.keys(ETIQUETTES_STATE.selected).forEach(id => {
            const st = ETIQUETTES_STATE.selected[id];
            if (st && st.checked) {
                booksCount++;
                labelsCount += Math.max(1, st.qty || 1);
            }
        });
        const fmt = LABEL_FORMATS[ETIQUETTES_STATE.format] || LABEL_FORMATS.AVERY_24;
        const offset = Math.max(1, Math.min(fmt.perPage, parseInt(ETIQUETTES_STATE.startOffset || 1, 10)));
        const totalSlots = labelsCount + (offset - 1);
        const pages = labelsCount === 0 ? 0 : Math.ceil(totalSlots / fmt.perPage);
        return { booksCount, labelsCount, pages, fmt, offset };
    }

    function buildGestionEtiquettesHtml() {
        const totals = getEtiquettesTotals();
        const q = ETIQUETTES_STATE.filterText.trim().toLowerCase();
        const books = CATALOG_BOOKS.filter(b => {
            const st = ETIQUETTES_STATE.selected[b.id];
            if (ETIQUETTES_STATE.onlyChecked && !(st && st.checked)) return false;
            if (q) {
                const hay = `${b.titre} ${b.auteur} ${b.isbn} ${b.cote}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });

        const focusBook = CATALOG_BOOKS.find(b => b.id === ETIQUETTES_STATE.focusedBookId) || CATALOG_BOOKS[0];

        return `
            <div class="toolbar-card">
                <div class="toolbar-row">
                    <span class="scan-badge">⚡ Ajout rapide</span>
                    <input id="etiqQuickInput" type="text" placeholder="Tapez un titre ou scannez l'ISBN puis Entrée pour cocher (ex: Dinosaures)…"
                        style="flex:1; border:none; outline:none; background:transparent; font-size:13px;"
                        onkeydown="if(event.key==='Enter'){ event.preventDefault(); quickAddEtiquetteBook(); }">
                    <button class="btn-forest-sm" onclick="quickAddEtiquetteBook()">➕ Cocher (Entrée)</button>
                    <button class="btn-soft-sm" onclick="toggleAllEtiquettes(true)">☑️ Tout cocher</button>
                    <button class="btn-soft-sm" onclick="toggleAllEtiquettes(false)">⬜ Tout décocher</button>
                    <button class="btn-soft-sm" onclick="selectRecentEtiquettes()">✨ Derniers ajouts</button>
                </div>
            </div>

            <div class="split-layout split-60-40">
                <div class="split-left panel-scroll" style="display:flex; flex-direction:column; gap:8px;">
                    <div class="toolbar-filters" style="padding:0 2px;">
                        <input type="text" value="${escapeHtml(ETIQUETTES_STATE.filterText)}" placeholder="🔍 Filtrer la liste…"
                            oninput="ETIQUETTES_STATE.filterText=this.value; renderGestionView();" style="flex:1;">
                        <label style="display:flex; align-items:center; gap:6px; font-size:12px; cursor:pointer;">
                            <input type="checkbox" ${ETIQUETTES_STATE.onlyChecked ? 'checked' : ''} onchange="ETIQUETTES_STATE.onlyChecked=this.checked; renderGestionView();">
                            Afficher uniquement cochés
                        </label>
                    </div>
                    ${books.map(b => {
                        const st = ETIQUETTES_STATE.selected[b.id] || { checked: false, qty: Math.max(1, b.exemplaires.length || 1) };
                        return `
                            <div class="user-row-card ${st.checked ? 'selected' : ''}" onclick="ETIQUETTES_STATE.focusedBookId='${b.id}'; renderGestionView();">
                                <input type="checkbox" ${st.checked ? 'checked' : ''} onclick="event.stopPropagation();" onchange="toggleEtiquetteBook('${b.id}', this.checked)">
                                <div class="book-cover" style="background:${b.gradient}; width:32px; height:42px; font-size:15px;">${b.emoji}</div>
                                <div style="flex:1; min-width:0;">
                                    <div style="font-weight:800; font-size:12.5px;">${escapeHtml(b.titre)}</div>
                                    <div class="muted-xs">${escapeHtml(b.auteur)} • ISBN ${escapeHtml(b.isbn)} • ${b.exemplaires.length} ex. en BCD</div>
                                </div>
                                <div class="stepper-box" onclick="event.stopPropagation();">
                                    <button onclick="stepEtiquetteQty('${b.id}', -1)">−</button>
                                    <span>${st.qty}</span>
                                    <button onclick="stepEtiquetteQty('${b.id}', 1)">+</button>
                                </div>
                            </div>
                        `;
                    }).join('')}
                </div>

                <div class="split-right detail-panel panel-scroll">
                    <div class="detail-panel-inner">
                        <div class="detail-section-card">
                            <h4>📊 Résumé de votre tirage</h4>
                            <div class="kpi-grid-3" style="margin-top:8px;">
                                <div class="kpi-mini-box"><div class="muted-xs">Livres</div><strong id="etiqSummaryBooks">${totals.booksCount}</strong></div>
                                <div class="kpi-mini-box"><div class="muted-xs">Étiquettes</div><strong id="etiqSummaryLabels" style="color:var(--forest);">${totals.labelsCount}</strong></div>
                                <div class="kpi-mini-box"><div class="muted-xs">Feuilles A4</div><strong id="etiqSummaryPages" style="color:var(--blue-book);">${totals.pages}</strong></div>
                            </div>
                            <div class="alert-inline-ok" style="margin-top:8px;">
                                ♻️ Anti-gaspillage : impression dès la <strong>case n°${totals.offset}</strong> (${totals.fmt.perPage} cases/page).
                            </div>
                        </div>

                        <div class="detail-section-card">
                            <h4>⚙️ Format & Réglages planche A4</h4>
                            <div class="form-stack" style="margin-top:8px;">
                                <label>Format de planche
                                    <select onchange="ETIQUETTES_STATE.format=this.value; renderGestionView();">
                                        ${Object.keys(LABEL_FORMATS).map(k => `<option value="${k}" ${ETIQUETTES_STATE.format === k ? 'selected' : ''}>${LABEL_FORMATS[k].name}</option>`).join('')}
                                    </select>
                                </label>
                                <div class="inline-form-row">
                                    <label style="width:140px;">Case de départ
                                        <input type="number" min="1" max="${totals.fmt.perPage}" value="${totals.offset}"
                                            onchange="ETIQUETTES_STATE.startOffset=parseInt(this.value||'1',10); renderGestionView();">
                                    </label>
                                    <label style="flex:1;">En-tête école
                                        <input type="text" value="${escapeHtml(ETIQUETTES_STATE.headerText)}"
                                            oninput="ETIQUETTES_STATE.headerText=this.value; updateEtiquetteLivePreview();">
                                    </label>
                                </div>
                                <div class="toolbar-filters">
                                    <label><input type="checkbox" ${ETIQUETTES_STATE.showTitle ? 'checked' : ''} onchange="ETIQUETTES_STATE.showTitle=this.checked; renderGestionView();"> Titre</label>
                                    <label><input type="checkbox" ${ETIQUETTES_STATE.showBarcode ? 'checked' : ''} onchange="ETIQUETTES_STATE.showBarcode=this.checked; renderGestionView();"> Code-barres</label>
                                    <label><input type="checkbox" ${ETIQUETTES_STATE.showBorders ? 'checked' : ''} onchange="ETIQUETTES_STATE.showBorders=this.checked; renderGestionView();"> Repères découpe</label>
                                </div>
                            </div>
                        </div>

                        <div class="detail-section-card" style="align-items:center;">
                            <h4 style="align-self:flex-start;">👁️ Aperçu d'une étiquette</h4>
                            <div id="etiqLivePreviewBox" class="label-preview-box ${ETIQUETTES_STATE.showBorders ? 'with-cut-borders' : ''}">
                                <div id="etiqPrevHeader" class="label-prev-header">${escapeHtml(ETIQUETTES_STATE.headerText)}</div>
                                ${ETIQUETTES_STATE.showTitle ? `<div class="label-prev-title">${escapeHtml(focusBook.titre)}</div>` : ''}
                                ${ETIQUETTES_STATE.showBarcode ? `<div class="barcode-bars" aria-hidden="true"></div>` : ''}
                                <div class="label-prev-code">${escapeHtml(focusBook.isbn)} • ${escapeHtml(focusBook.exemplaires[0]?.id || '#101')}</div>
                            </div>
                        </div>

                        <button class="btn-forest" style="width:100%; justify-content:center; padding:11px;" onclick="openA4SheetPreviewModal()">
                            🖨️ Générer & Aperçu Planche A4 (${totals.labelsCount} étiquette${totals.labelsCount > 1 ? 's' : ''})
                        </button>
                    </div>
                </div>
            </div>
        `;
    }

    function updateEtiquetteLivePreview() {
        const el = document.getElementById('etiqPrevHeader');
        if (el) el.textContent = ETIQUETTES_STATE.headerText;
    }

    function toggleEtiquetteBook(bookId, checked) {
        const bk = CATALOG_BOOKS.find(b => b.id === bookId);
        const cur = ETIQUETTES_STATE.selected[bookId] || { qty: Math.max(1, bk?.exemplaires.length || 1) };
        ETIQUETTES_STATE.selected[bookId] = { checked, qty: cur.qty };
        ETIQUETTES_STATE.focusedBookId = bookId;
        renderGestionView();
    }

    function stepEtiquetteQty(bookId, delta) {
        const bk = CATALOG_BOOKS.find(b => b.id === bookId);
        const cur = ETIQUETTES_STATE.selected[bookId] || { checked: true, qty: Math.max(1, bk?.exemplaires.length || 1) };
        const next = Math.max(1, Math.min(20, (cur.qty || 1) + delta));
        ETIQUETTES_STATE.selected[bookId] = { checked: true, qty: next };
        ETIQUETTES_STATE.focusedBookId = bookId;
        renderGestionView();
    }

    function quickAddEtiquetteBook() {
        const q = (document.getElementById('etiqQuickInput')?.value || '').trim().toLowerCase();
        const match = CATALOG_BOOKS.find(b => !q || b.titre.toLowerCase().includes(q) || b.isbn.toLowerCase().includes(q));
        if (!match) {
            notify('Aucun livre correspondant trouvé', '⚠️');
            return;
        }
        ETIQUETTES_STATE.selected[match.id] = { checked: true, qty: Math.max(1, match.exemplaires.length || 1) };
        ETIQUETTES_STATE.focusedBookId = match.id;
        chime();
        renderGestionView();
        notify(`Coché pour étiquette : <strong>${escapeHtml(match.titre)}</strong>`, '🏷️');
    }

    function toggleAllEtiquettes(flag) {
        CATALOG_BOOKS.forEach(b => {
            const cur = ETIQUETTES_STATE.selected[b.id];
            ETIQUETTES_STATE.selected[b.id] = { checked: flag, qty: cur?.qty || Math.max(1, b.exemplaires.length || 1) };
        });
        renderGestionView();
    }

    function selectRecentEtiquettes() {
        CATALOG_BOOKS.forEach((b, idx) => {
            ETIQUETTES_STATE.selected[b.id] = { checked: idx < 4, qty: Math.max(1, b.exemplaires.length || 1) };
        });
        chime();
        renderGestionView();
        notify('4 derniers ajouts sélectionnés', '✨');
    }

    function openA4SheetPreviewModal() {
        const totals = getEtiquettesTotals();
        if (totals.labelsCount === 0) {
            notify('Cochez au moins un livre pour générer une planche', '⚠️');
            return;
        }
        const slots = [];
        for (let i = 1; i < totals.offset; i++) {
            slots.push({ empty: true, idx: i });
        }
        Object.keys(ETIQUETTES_STATE.selected).forEach(bookId => {
            const st = ETIQUETTES_STATE.selected[bookId];
            if (!st || !st.checked) return;
            const bk = CATALOG_BOOKS.find(b => b.id === bookId);
            if (!bk) return;
            for (let q = 0; q < st.qty; q++) {
                if (slots.length < totals.fmt.perPage) {
                    const exLabel = bk.exemplaires[q]?.id || `#${101 + q}`;
                    slots.push({ empty: false, book: bk, exLabel });
                }
            }
        });
        while (slots.length < Math.min(12, totals.fmt.perPage)) {
            slots.push({ empty: true, idx: slots.length + 1 });
        }

        openModal(
            `🖨️ Aperçu Planche A4 (Page 1/${totals.pages})`,
            `<div class="a4-preview-sheet">
                <p class="muted-xs" style="margin-bottom:8px;">${escapeHtml(totals.fmt.name)} • Départ case n°${totals.offset}</p>
                <div class="a4-sheet-grid" style="grid-template-columns: repeat(${totals.fmt.cols}, 1fr);">
                    ${slots.map(s => s.empty
                        ? `<div class="a4-cell skipped">Case ${s.idx} (déjà utilisée)</div>`
                        : `<div class="a4-cell">
                               <div class="label-prev-header">${escapeHtml(ETIQUETTES_STATE.headerText)}</div>
                               ${ETIQUETTES_STATE.showTitle ? `<div class="label-prev-title">${escapeHtml(s.book.titre)}</div>` : ''}
                               ${ETIQUETTES_STATE.showBarcode ? `<div class="barcode-bars small"></div>` : ''}
                               <div class="label-prev-code">${escapeHtml(s.book.cote)} • ${escapeHtml(s.exLabel)}</div>
                           </div>`
                    ).join('')}
                </div>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Fermer</button>
             <button class="btn-forest" onclick="closeModal(); chime(); notify('Planche A4 envoyée vers l’impression', '🖨️')">🖨️ Lancer l’impression</button>`
        );
    }

    // --- 8C. GESTION > RÉCOLEMENT & INVENTAIRE ---
    function buildGestionRecolementHtml() {
        const items = RECOLEMENT_STATE.items;
        const total = items.length;
        const present = items.filter(i => i.statut === 'PRESENT').length;
        const enPret = items.filter(i => i.statut === 'EN_PRET').length;
        const manquant = items.filter(i => i.statut === 'MANQUANT').length;
        const verified = present + enPret;
        const pct = total === 0 ? 0 : Math.round((verified / total) * 100);

        const q = RECOLEMENT_STATE.search.trim().toLowerCase();
        const filtered = items.filter(i => {
            if (RECOLEMENT_STATE.filterStatus !== 'ALL' && i.statut !== RECOLEMENT_STATE.filterStatus) return false;
            if (q && !`${i.exId} ${i.titre} ${i.isbn}`.toLowerCase().includes(q)) return false;
            return true;
        });

        return `
            <div class="recolement-banner ${RECOLEMENT_STATE.sessionActive ? 'frozen' : ''}">
                <span class="status-pill ${RECOLEMENT_STATE.sessionActive ? 'pill-terracotta' : 'pill-forest'}">
                    ${RECOLEMENT_STATE.sessionActive ? '🔒 Gel de stock actif' : '🟢 Mode Veille'}
                </span>
                <span style="flex:1; font-size:12.5px; font-weight:600;">
                    ${RECOLEMENT_STATE.sessionActive
                        ? `Campagne en cours (${escapeHtml(RECOLEMENT_STATE.perimetre)}) — Les mouvements de prêts sont gelés.`
                        : 'Aucune campagne active — Les prêts et retours au Comptoir fonctionnent normalement.'}
                </span>
                ${!RECOLEMENT_STATE.sessionActive
                    ? `<button class="btn-forest" onclick="startRecolementSession()">🚀 Démarrer un inventaire</button>`
                    : `<div class="header-actions">
                           <button class="btn-forest" onclick="closeRecolementSession()">✅ Clôturer (PV)</button>
                           <button class="btn-soft" onclick="RECOLEMENT_STATE.sessionActive=false; renderGestionView();">✕ Abandonner</button>
                       </div>`
                }
            </div>

            <div class="kpi-grid-4">
                <div class="kpi-card-warm"><span>📚</span><div><div class="muted-xs">Périmètre suivi</div><strong>${total}</strong></div></div>
                <div class="kpi-card-warm"><span>✅</span><div><div class="muted-xs">Présents scannés</div><strong style="color:var(--forest);">${present}</strong></div></div>
                <div class="kpi-card-warm"><span>👤</span><div><div class="muted-xs">En prêt justifiés</div><strong style="color:var(--blue-book);">${enPret}</strong></div></div>
                <div class="kpi-card-warm"><span>⚠️</span><div><div class="muted-xs">À vérifier / Manquants</div><strong style="color:var(--terracotta);">${manquant}</strong></div></div>
            </div>

            <div class="toolbar-card">
                <div class="toolbar-row" style="gap:16px;">
                    <div style="width:280px;">
                        <div style="display:flex; justify-content:space-between; font-size:11.5px; font-weight:700; margin-bottom:5px;">
                            <span>Progression campagne</span><span>${pct}% (${verified}/${total})</span>
                        </div>
                        <div class="quota-bar-bg" style="width:100%; height:8px;">
                            <div class="quota-bar-fill" style="width:${pct}%;"></div>
                        </div>
                    </div>
                    <div class="search-wrap" style="flex:1;">
                        <span>⚡</span>
                        <input id="recScanInput" type="text" placeholder="Scanner sur l'étagère : N° ex. (#115, #121), ISBN ou titre…"
                            onkeydown="if(event.key==='Enter'){ event.preventDefault(); scanRecolementItem(); }">
                        <button class="btn-forest-sm" onclick="scanRecolementItem()">Bipper</button>
                    </div>
                </div>
                <div class="toolbar-filters">
                    <button class="sc-btn ${RECOLEMENT_STATE.filterStatus === 'ALL' ? 'primary' : ''}" onclick="RECOLEMENT_STATE.filterStatus='ALL'; renderGestionView();">Tous (${total})</button>
                    <button class="sc-btn ${RECOLEMENT_STATE.filterStatus === 'MANQUANT' ? 'primary' : ''}" onclick="RECOLEMENT_STATE.filterStatus='MANQUANT'; renderGestionView();">⚠️ À vérifier (${manquant})</button>
                    <button class="sc-btn ${RECOLEMENT_STATE.filterStatus === 'PRESENT' ? 'primary' : ''}" onclick="RECOLEMENT_STATE.filterStatus='PRESENT'; renderGestionView();">✅ Présents (${present})</button>
                    <button class="sc-btn ${RECOLEMENT_STATE.filterStatus === 'EN_PRET' ? 'primary' : ''}" onclick="RECOLEMENT_STATE.filterStatus='EN_PRET'; renderGestionView();">👤 En prêt (${enPret})</button>
                    <input type="text" value="${escapeHtml(RECOLEMENT_STATE.search)}" placeholder="🔍 Filtrer…" style="margin-left:auto;"
                        oninput="RECOLEMENT_STATE.search=this.value; renderGestionView();">
                </div>
            </div>

            <div class="catalog-table-wrap panel-scroll" style="flex:1;">
                <table class="warm-table">
                    <thead>
                        <tr><th>Ex.</th><th>Ouvrage</th><th>Rayon</th><th>Statut</th><th>Situation / Motif</th><th>Actions directes</th></tr>
                    </thead>
                    <tbody>
                        ${filtered.map(it => {
                            const stCls = it.statut === 'PRESENT' ? 'pill-forest' : (it.statut === 'EN_PRET' ? 'pill-gold' : 'pill-terracotta');
                            const stTxt = it.statut === 'PRESENT' ? '✅ Présent' : (it.statut === 'EN_PRET' ? '👤 En prêt' : '⚠️ À vérifier');
                            return `
                                <tr>
                                    <td><span class="mono-badge">${escapeHtml(it.exId)}</span></td>
                                    <td><strong>${escapeHtml(it.titre)}</strong><br><span class="muted-xs">${escapeHtml(it.auteur)}</span></td>
                                    <td><span class="muted-xs">${escapeHtml(it.zoneLabel)}</span></td>
                                    <td><span class="status-pill ${stCls}">${stTxt}</span></td>
                                    <td><span class="muted-xs">${escapeHtml(it.motif || it.info)}</span></td>
                                    <td>
                                        <div style="display:flex; gap:6px;">
                                            ${it.statut !== 'PRESENT' ? `<button class="btn-forest-sm" onclick="markRecolementPresent('${escapeHtml(it.exId)}')">✅ Présent</button>` : ''}
                                            <button class="btn-soft-sm" onclick="openJustifyEcartModal('${escapeHtml(it.exId)}')">✏️ Écart</button>
                                        </div>
                                    </td>
                                </tr>
                            `;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    function startRecolementSession() {
        RECOLEMENT_STATE.sessionActive = true;
        chime();
        renderGestionView();
        notify('Campagne de récolement démarrée (gel de stock actif)', '🚀');
    }

    function closeRecolementSession() {
        const present = RECOLEMENT_STATE.items.filter(i => i.statut === 'PRESENT').length;
        const total = RECOLEMENT_STATE.items.length;
        RECOLEMENT_STATE.sessionActive = false;
        chime();
        renderGestionView();
        openModal(
            '✅ Procès-verbal de Récolement généré',
            `<p style="font-size:13px; line-height:1.5;">Inventaire clôturé pour l’école Albert Camus Pasteur.<br><strong>${present}</strong> exemplaires pointés présents sur <strong>${total}</strong> suivis.</p>`,
            `<button class="btn-forest" onclick="closeModal()">Parfait</button>`
        );
    }

    function scanRecolementItem() {
        const raw = (document.getElementById('recScanInput')?.value || '').trim().toLowerCase();
        if (!raw) {
            const next = RECOLEMENT_STATE.items.find(i => i.statut === 'MANQUANT');
            if (next) markRecolementPresent(next.exId);
            return;
        }
        const match = RECOLEMENT_STATE.items.find(i =>
            i.exId.toLowerCase().includes(raw) ||
            i.isbn.toLowerCase().includes(raw) ||
            i.titre.toLowerCase().includes(raw)
        );
        if (!match) {
            notify(`Aucun exemplaire trouvé pour « ${escapeHtml(raw)} »`, '⚠️');
            return;
        }
        markRecolementPresent(match.exId);
    }

    function markRecolementPresent(exId) {
        const it = RECOLEMENT_STATE.items.find(i => i.exId === exId);
        if (!it) return;
        it.statut = 'PRESENT';
        it.info = 'Pointé présent à l’instant';
        chime();
        renderGestionView();
        notify(`Exemplaire <strong>${escapeHtml(exId)}</strong> (${escapeHtml(it.titre)}) validé présent`, '✅');
    }

    function openJustifyEcartModal(exId) {
        const it = RECOLEMENT_STATE.items.find(i => i.exId === exId);
        if (!it) return;
        openModal(
            `✏️ Qualifier l’écart — ${escapeHtml(it.exId)}`,
            `<div class="form-stack">
                <p><strong>${escapeHtml(it.titre)}</strong></p>
                <label>Motif constaté en rayon
                    <select id="ecartMotifSelect">
                        <option>Égaré en classe (à vérifier)</option>
                        <option>En réparation / reliure</option>
                        <option>Pilon (ouvrage trop abîmé)</option>
                        <option>Erreur de rayonnage corrigée</option>
                    </select>
                </label>
            </div>`,
            `<button class="btn-soft" onclick="closeModal()">Annuler</button>
             <button class="btn-forest" onclick="confirmJustifyEcart('${escapeHtml(exId)}')">Enregistrer</button>`
        );
    }

    function confirmJustifyEcart(exId) {
        const it = RECOLEMENT_STATE.items.find(i => i.exId === exId);
        const motif = document.getElementById('ecartMotifSelect')?.value || '';
        if (it) it.motif = motif;
        closeModal();
        chime();
        renderGestionView();
        notify(`Motif enregistré pour ${escapeHtml(exId)}`, '✏️');
    }

    // --- 8D. GESTION > SAUVEGARDES & OUTILS ---
    function buildGestionOutilsHtml() {
        const anomalies = CATALOG_BOOKS.filter(b => b.exemplaires.length === 0);
        return `
            <div class="split-layout split-50-50">
                <div class="detail-panel panel-scroll">
                    <div class="detail-panel-inner">
                        <div class="section-row-head">
                            <h4>💾 Sauvegardes SQLite locales (bcd.db)</h4>
                            <div style="display:flex; gap:6px;">
                                <button class="btn-forest-sm" onclick="createNewBackup()">💾 Créer une sauvegarde</button>
                                <button class="btn-soft-sm" onclick="notify('Export prêt pour copie sur clé USB', '🔌')">🔌 Clé USB</button>
                            </div>
                        </div>
                        <p class="muted-xs">Base 100 % locale sans serveur. Une copie de sécurité est aussi créée automatiquement à chaque fermeture.</p>
                        <div class="copies-list">
                            ${BACKUPS_DATA.map(b => `
                                <div class="copy-row">
                                    <span class="mono-badge">${escapeHtml(b.size)}</span>
                                    <div style="flex:1; min-width:0;">
                                        <div style="font-weight:700; font-size:12px;">${escapeHtml(b.file)}</div>
                                        <div class="muted-xs">${escapeHtml(b.date)} • ${escapeHtml(b.type)}</div>
                                    </div>
                                    <button class="btn-soft-sm" onclick="notify('Restauration simulée depuis ${escapeHtml(b.file)}', '↩️')">Restaurer</button>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                </div>

                <div class="detail-panel panel-scroll">
                    <div class="detail-panel-inner">
                        <div class="section-row-head">
                            <h4>📦 Boîte à vérifier & Anomalies (${anomalies.length})</h4>
                            <button class="btn-soft-sm" onclick="notify('Classification Dewey & zones BCD vérifiée sur 12 titres', '🪄')">🪄 Classifier tout</button>
                        </div>
                        ${anomalies.length === 0
                            ? `<div class="alert-inline-ok">✨ Aucune anomalie détectée dans le fonds ! Tous les titres ont au moins un exemplaire physique.</div>`
                            : `<div class="copies-list">
                                ${anomalies.map(b => `
                                    <div class="copy-row" style="background:var(--terracotta-light); border-color:#F3C4B4;">
                                        <span>⚠️</span>
                                        <div style="flex:1;">
                                            <strong>${escapeHtml(b.titre)}</strong>
                                            <div class="muted-xs">0 exemplaire physique rattaché • ${escapeHtml(b.isbn)}</div>
                                        </div>
                                        <button class="btn-forest-sm" onclick="fixZeroCopyAnomaly('${b.id}')">➕ Créer 1 exemplaire</button>
                                    </div>
                                `).join('')}
                            </div>`
                        }

                        <div class="detail-section-card" style="margin-top:8px;">
                            <h4>🧭 Outils rapides</h4>
                            <div class="header-actions" style="margin-top:8px; flex-wrap:wrap;">
                                <button class="btn-soft" onclick="notify('Catalogue exporté en CSV UTF-8 (compatible Excel)', '📤')">📤 Exporter catalogue CSV</button>
                                <a class="btn-soft" href="plan_2d_maquette_3d_bcd.html" target="_blank" style="text-decoration:none;">🗺️ Plan 2D / Maquette 3D complète</a>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    function createNewBackup() {
        const now = new Date();
        const hh = String(now.getHours()).padStart(2, '0');
        const mm = String(now.getMinutes()).padStart(2, '0');
        BACKUPS_DATA.unshift({
            file: `bcd_camus_2026-11-26_${hh}h${mm}.db`,
            date: `À l’instant (${hh}h${mm})`,
            size: '1,43 Mo',
            type: 'Sauvegarde manuelle'
        });
        chime();
        renderGestionView();
        notify('Sauvegarde SQLite locale créée avec succès', '💾');
    }

    function fixZeroCopyAnomaly(bookId) {
        const bk = CATALOG_BOOKS.find(b => b.id === bookId);
        if (!bk) return;
        bk.exemplaires.push({ id: `#${400 + CATALOG_BOOKS.length}`, statut: 'DISPONIBLE', dateAjout: "Aujourd'hui", etat: 'Neuf' });
        rebuildRecolementItems();
        chime();
        renderGestionView();
        notify(`Anomalie corrigée : exemplaire créé pour <strong>${escapeHtml(bk.titre)}</strong>`, '✅');
    }

    // ------------------------------------------------------------------------
    // 9. INITIALISATION & EXPOSITION GLOBALE POUR LES HANDLERS HTML
    // ------------------------------------------------------------------------
    rebuildRecolementItems();

    window.switchMainView = switchMainView;
    window.openModal = openModal;
    window.closeModal = closeModal;
    window.locateBookOnComptoir = locateBookOnComptoir;
    window.sendBookToEtiquettes = sendBookToEtiquettes;
    window.openUserAtComptoir = openUserAtComptoir;

    window.renderLivresView = renderLivresView;
    window.onLivresSearch = onLivresSearch;
    window.setLivresViewMode = setLivresViewMode;
    window.selectCatalogBook = selectCatalogBook;
    window.resetLivresFilters = resetLivresFilters;
    window.addPhysicalCopy = addPhysicalCopy;
    window.addBookResource = addBookResource;
    window.confirmAddBookResource = confirmAddBookResource;
    window.openUnifiedBookIntake = openUnifiedBookIntake;
    window.onIntakeInput = onIntakeInput;
    window.handleIntakeSubmit = handleIntakeSubmit;
    window.intakeDemo = intakeDemo;
    window.addExistingCatalogCopyFromIntake = addExistingCatalogCopyFromIntake;
    window.addApiBookDirect = addApiBookDirect;
    window.prefillManualFromApi = prefillManualFromApi;
    window.prepareManualFromQuery = prepareManualFromQuery;
    window.toggleIntakeNoBarcode = toggleIntakeNoBarcode;
    window.clearUnifiedManualForm = clearUnifiedManualForm;
    window.submitUnifiedManualBook = submitUnifiedManualBook;
    window.removeSessionIntakeItem = removeSessionIntakeItem;
    window.sendSessionToEtiquettes = sendSessionToEtiquettes;
    window.INTAKE_STATE = INTAKE_STATE;
    window.API_SIMULATED_BOOKS = API_SIMULATED_BOOKS;
    window.openImportLivresCsvModal = openImportLivresCsvModal;
    window.LIVRES_STATE = LIVRES_STATE;

    window.renderMallettesView = renderMallettesView;
    window.onMalletteSearch = onMalletteSearch;
    window.selectMallette = selectMallette;
    window.setMalletteSubTab = setMalletteSubTab;
    window.stepMalletteItem = stepMalletteItem;
    window.addBookToMallette = addBookToMallette;
    window.fillMalletteByTheme = fillMalletteByTheme;
    window.addMalletteResource = addMalletteResource;
    window.openLoanMalletteModal = openLoanMalletteModal;
    window.confirmLoanMallette = confirmLoanMallette;
    window.returnMallette = returnMallette;
    window.openMallettePrintPreview = openMallettePrintPreview;
    window.openCreateMalletteModal = openCreateMalletteModal;
    window.confirmCreateMallette = confirmCreateMallette;
    window.MALLETTES_STATE = MALLETTES_STATE;

    window.renderGestionView = renderGestionView;
    window.switchGestionTab = switchGestionTab;
    window.onGestionUserSearch = onGestionUserSearch;
    window.selectGestionUser = selectGestionUser;
    window.openAddUserModal = openAddUserModal;
    window.confirmAddUser = confirmAddUser;
    window.GESTION_STATE = GESTION_STATE;

    window.updateEtiquetteLivePreview = updateEtiquetteLivePreview;
    window.toggleEtiquetteBook = toggleEtiquetteBook;
    window.stepEtiquetteQty = stepEtiquetteQty;
    window.quickAddEtiquetteBook = quickAddEtiquetteBook;
    window.toggleAllEtiquettes = toggleAllEtiquettes;
    window.selectRecentEtiquettes = selectRecentEtiquettes;
    window.openA4SheetPreviewModal = openA4SheetPreviewModal;
    window.ETIQUETTES_STATE = ETIQUETTES_STATE;

    window.startRecolementSession = startRecolementSession;
    window.closeRecolementSession = closeRecolementSession;
    window.scanRecolementItem = scanRecolementItem;
    window.markRecolementPresent = markRecolementPresent;
    window.openJustifyEcartModal = openJustifyEcartModal;
    window.confirmJustifyEcart = confirmJustifyEcart;
    window.RECOLEMENT_STATE = RECOLEMENT_STATE;

    window.renderProfileSplash = renderProfileSplash;
    window.openProfilePicker = openProfilePicker;
    window.closeProfilePicker = closeProfilePicker;
    window.chooseProfile = chooseProfile;
    window.submitAdminPin = submitAdminPin;
    window.handleBadgeLogin = handleBadgeLogin;
    window.applySessionToUI = applySessionToUI;
    window.openClassReminderCouponsModal = openClassReminderCouponsModal;
    window.PROFILES_DATA = PROFILES_DATA;
    window.SESSION_STATE = SESSION_STATE;

    window.createNewBackup = createNewBackup;
    window.fixZeroCopyAnomaly = fixZeroCopyAnomaly;
    window.CATALOG_BOOKS = CATALOG_BOOKS;
    window.MALLETTES_DATA = MALLETTES_DATA;
    window.USERS_DATA = USERS_DATA;
    window.BACKUPS_DATA = BACKUPS_DATA;

    renderProfileSplash();
    applySessionToUI(true);
})();
