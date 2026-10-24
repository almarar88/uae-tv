# Credits · المصادر والحقوق

All third-party material used by **مجلس · Majlis TV**, with its source and licence.
Nothing in this project is copied from tshi-dev/UrTV.

## Background photos (Wikimedia Commons)

Real photographs, not AI-generated. Each was downloaded from Wikimedia Commons, then **cropped to 16:9 and resized to 1920×1080 (JPEG)** for this app; no other edits.
The photographer and licence are also shown on screen under each photo.

| File | Subject | Commons page | Author | Licence |
|---|---|---|---|---|
| `app/src/main/assets/web/bg/mosque.jpg` | قاعة الصلاة في جامع الشيخ زايد الكبير — أبوظبي | [Abu dhabi, Moschea dello Sceicco Zayed, aula di preghiera, 03.jpg](https://commons.wikimedia.org/wiki/File:Abu_dhabi,_Moschea_dello_Sceicco_Zayed,_aula_di_preghiera,_03.jpg) | Francesco Bini | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| `app/src/main/assets/web/bg/liwa.jpg` | غروب على كثبان ليوا — منطقة الظفرة | [Liwa Sunsets.jpg](https://commons.wikimedia.org/wiki/File:Liwa_Sunsets.jpg) | Almzo7e | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| `app/src/main/assets/web/bg/abudhabi.jpg` | كورنيش أبوظبي | [Abu Dhabi - Panorama - Corniche – Skyline - بانوراما - الكورنيش - الأفق - panoramio (1).jpg](https://commons.wikimedia.org/wiki/File:Abu_Dhabi_-_Panorama_-_Corniche_%E2%80%93_Skyline_-_%D8%A8%D8%A7%D9%86%D9%88%D8%B1%D8%A7%D9%85%D8%A7_-_%D8%A7%D9%84%D9%83%D9%88%D8%B1%D9%86%D9%8A%D8%B4_-_%D8%A7%D9%84%D8%A3%D9%81%D9%82_-_panoramio_(1).jpg) | giggel | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0) |
| `app/src/main/assets/web/bg/dubai.jpg` | برج خليفة وأفق دبي | [Burj Khalifa (worlds tallest building) and the Dubai skyline (25781049892).jpg](https://commons.wikimedia.org/wiki/File:Burj_Khalifa_(worlds_tallest_building)_and_the_Dubai_skyline_(25781049892).jpg) | imran shahabuddin | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `app/src/main/assets/web/bg/hatta.jpg` | سد حتا — دبي | [Spectacular view of Hatta Dam surrounded by mountains.jpg](https://commons.wikimedia.org/wiki/File:Spectacular_view_of_Hatta_Dam_surrounded_by_mountains.jpg) | Clintonrebeiro | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| `app/src/main/assets/web/bg/alain.jpg` | واحة العين | [Al-Ain Oasis (2).jpg](https://commons.wikimedia.org/wiki/File:Al-Ain_Oasis_(2).jpg) | Radosław Botev | [CC BY 3.0 pl](https://creativecommons.org/licenses/by/3.0/pl/deed.en) |
| `app/src/main/assets/web/bg/hosn.jpg` | قصر الحصن — أبوظبي | [Qasr al Hosn.jpg](https://commons.wikimedia.org/wiki/File:Qasr_al_Hosn.jpg) | Darthjipsu | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |

CC BY-SA images: the resized versions in this repo are shared under the same licence as the original.

## Quran text

- **Tanzil Quran Text (Uthmani), Tanzil Project — https://tanzil.net** (`app/src/main/assets/quran-uthmani.txt`).
  Included verbatim, unmodified, with its copyright notice kept at the end of the file, as required by the Tanzil terms of use.
- Sura metadata (names, verse counts, Meccan/Medinan): Tanzil `quran-data.js` (`assets/web/suras.js`).

## Tafsir (verse explanation)

Fetched at runtime from the public **alquran.cloud** API (https://alquran.cloud/api):
- `ar.muyassar`: التفسير الميسر, King Fahd Glorious Quran Printing Complex
- `ar.jalalayn`: تفسير الجلالين

The AI explanation is grounded only in these texts and numbers its sources [1] and [2]. The original tafsir text is shown under the explanation.

## Fonts (SIL Open Font License 1.1)

- **Noto Kufi Arabic**, Google / Noto project ([OFL](licenses/OFL-NotoKufiArabic.txt))
- **Amiri Quran**, Khaled Hosny ([OFL](licenses/OFL-AmiriQuran.txt))

## Data services

- Weather: **Open-Meteo.com**, CC BY 4.0
- Football: **TheSportsDB.com**, free public API (key `123`). The free tier returns only the top 5 table rows and 1 upcoming/past fixture. The app shows only what the API returns and hides the section when there is no data.
- Prayer times: calculated on the device (original code). No external service is used.

## Code

- Original code © 2026 Ahmed Almarar, MIT licence (see `LICENSE`).
- Concept inspired by the feature list of tshi-dev/UrTV («تلفزيونك»). That repo has no licence, so **no code, assets, text or design were taken from it**.

## 1.2.0
- **Icons:** Streamline, «Flex Line – Free» set (https://www.streamlinehq.com), used under Streamline's free licence with attribution. Files are in `app/src/main/assets/web/icons/`.
- **Font:** Noto Naskh Arabic by Google, SIL Open Font License 1.1.
- **Artwork enrichment:** images and summaries come from Wikipedia/Wikimedia (CC BY-SA) and are shown only when the match is confident. They are always labelled «ويكيبيديا».
- **App artwork:** comes from the installed apps themselves at runtime (TV banner or adaptive icon). The repository contains no third-party logos.
