# 👨‍💻 Kacper Wernerowicz — Interaktywne E-CV

[![Live Demo](https://img.shields.io/badge/🌐_Demo-Online-4CAF50?style=for-the-badge)](https://snazzy-chaja-20f64f.netlify.app/)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react)](https://react.dev/)
[![Bootstrap](https://img.shields.io/badge/Bootstrap-5.3-7952B3?style=for-the-badge&logo=bootstrap)](https://getbootstrap.com/)
[![Netlify Status](https://img.shields.io/badge/Deploy-Netlify-00C7B7?style=for-the-badge&logo=netlify)](https://snazzy-chaja-20f64f.netlify.app/)

Responsywna strona-portfolio zbudowana w **React 18** z profesjonalnym designem, trybem ciemnym i deploymentem na Netlify. Aplikacja prezentuje doświadczenie zawodowe, umiejętności techniczne, certyfikaty państwowe i projekty programistyczne w nowoczesnym, czytelnym formacie.

---

## ✨ Funkcje

- 🌗 **Tryb ciemny / jasny** — z zapisem preferencji w `localStorage`
- 🏷️ **System badge'ów** — umiejętności przedstawione jako profesjonalne odznaki z legendą (Codzienny stack / Doświadczenie)
- 🏆 **Karty certyfikatów** — INF.03 i INF.04 jako Achievement Cards z listą kompetencji i statusem
- ⏱️ **Zegar cyfrowy** — aktualny czas w nagłówku
- 📱 **Responsive design** — optymalizacja dla urządzeń mobilnych
- 🎯 **SEO** — meta tagi Open Graph, Twitter Card, semantyczny HTML
- 💚 **Spójny design system** — zielone akcenty, karty z lewym border, hover effects

## 📸 Podgląd

| Light Mode | Dark Mode |
|:-:|:-:|
| ![Light](https://via.placeholder.com/400x250/F8F9FA/2A2A2A?text=Light+Mode) | ![Dark](https://via.placeholder.com/400x250/121212/E0E0E0?text=Dark+Mode) |

> 💡 *Zamień placeholdery na rzeczywiste screenshoty po deploymencie.*

## 🛠️ Stack technologiczny

| Technologia | Zastosowanie |
|---|---|
| **React 18** | UI framework (komponentowa architektura) |
| **Bootstrap 5.3** | System gridowy, karty, badge'e, responsywność |
| **react-icons** | Ikony (FontAwesome, SimpleIcons, Devicons) |
| **react-awesome-reveal** | Animacje wejścia sekcji |
| **CSS3** | Custom design system (dark mode, hover effects, zmienne CSS) |
| **Netlify** | Hosting i CI/CD |

## 📁 Struktura projektu

```
src/
├── components/         # Komponenty React
│   ├── Header.jsx      # Nagłówek — karta wizytówkowa z dark mode toggle
│   ├── Projects.jsx    # Karty projektów z badge'ami technologii
│   ├── Experience.jsx  # Timeline doświadczenia z checkmarkami
│   ├── Education.jsx   # Karty wykształcenia (grid 2-kolumnowy)
│   ├── Skills.jsx      # Badge'e umiejętności + karty soft skills
│   ├── Certificates.jsx# Achievement cards INF.03 / INF.04
│   ├── Languages.jsx   # Poziomy językowe z badge'ami
│   ├── Contact.jsx     # Dane kontaktowe
│   ├── Footer.jsx      # Stopka z klauzulą RODO
│   └── DigitalClock.jsx# Komponent zegara
├── data/               # Dane (oddzielone od widoków)
│   ├── experience.js
│   ├── education.js
│   ├── skills.js
│   ├── certificates.js
│   ├── projects.js
│   ├── languages.js
│   └── contact.js
├── styles/
│   └── App.css         # Design system + dark mode overridy
└── index.js            # Entry point + Bootstrap CSS import
```

## 🚀 Uruchomienie lokalne

```bash
# Klonowanie repozytorium
git clone https://github.com/karpi17/cv.git
cd cv

# Instalacja zależności
npm install

# Serwer deweloperski
npm start
```

Aplikacja będzie dostępna pod adresem `http://localhost:3000`.

## 📦 Build produkcyjny

```bash
npm run build
```

## 📝 Licencja

Projekt prywatny — wszelkie prawa zastrzeżone © 2026 Kacper Wernerowicz.

---

<p align="center">
  Zbudowane z 💚 w <strong>React</strong>
</p>
