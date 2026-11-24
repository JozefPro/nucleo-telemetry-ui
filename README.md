# Kompiuterinės komunikacijos · LD2

KTU T170B008 laboratorinis darbas maketui Nr. 57. JavaFX programa per jSerialComm skaito esamos STM32 LD1 programos paketus ir rodo LIS2DUXS12 pagreitį bei GPS duomenis. STM32 pusėje naudojama LD1 programa.

## Paleidimas

Reikia JDK 23 arba naujesnio. Su JDK 25 išbandyta „macOS“ sistemoje. Pirmam paleidimui reikia interneto ryšio, kad „Maven Wrapper“ atsisiųstų Maven ir priklausomybes. VS Code atidarykite aplanką **komp-komunikacijos-ld2**, kuriame yra šis failas ir `pom.xml`.

**macOS**

```sh
java -version
./mvnw clean verify
./mvnw javafx:run
```

**Windows PowerShell**

```powershell
java -version
.\mvnw.cmd clean verify
.\mvnw.cmd javafx:run
```

`clean verify` sukompiliuoja projektą ir paleidžia JUnit testus. Jei Maven jau įdiegtas, tas pačias užduotis galima paleisti su `mvn clean verify` ir `mvn javafx:run`.

## Bandymas su maketu

1. Prijunkite LD1 maketą per USB–UART adapterį. Naudojama USART6 konfigūracija yra **921 600 baud, 8 duomenų bitai, 1 stop bitas, be pariteto, be srauto valdymo**.
2. LD2 aprašo 4 žingsniui „Mac“ terminale su įdiegtu `picocom` patikrinkite originalius paketus. Jei jūsų prievado pavadinimas skiriasi, jį rasite su `ls /dev/cu.*`.

   ```sh
   picocom --baud 921600 --databits 8 --parity n --stopbits 1 --flow n /dev/cu.usbserial-0001
   ```

   Uždarykite `picocom` paspausdami **Ctrl+A**, tada **Ctrl+X**, kad prievadą galėtų atidaryti JavaFX programa.

3. Paleiskite programą, iš sąrašo pasirinkite savo prievadą ir spauskite **Prisijungti**. „Mac“ prievadas gali vadintis `/dev/cu.usbserial-0001`, o „Windows“ sistemoje, pavyzdžiui, `COM4`. Prijungę adapterį jau atidarius langą, spauskite **Atnaujinti**.
4. Palyginkite naujausią terminalo eilutę su GPS laukeliais. Pavyzdyje `LAT=5453.9492 N` ir `LON=2354.5332 E` atitinka **54.899153° N** ir **23.908887° E**, o EET ir SAT yra **22:37:29** ir **11**. EET rodomas tiesiai iš paketo. Kai LD1 siunčia `GPS=WAIT`, grafike rodomas pagreitis, o GPS laukeliuose lieka brūkšniai.
5. Pakreipkite maketą į abi puses per X ir Y ašis. Stebėkite AX, AY bei AZ kreives ir dėžutę skydelyje **Jutiklio padėtis**. Grafikas rodo originalias atskaitas paskutinių 30 sekundžių intervale. Dėžutės judesys išlyginamas pagal pagreitį ir rodo pasvirimą per dvi ašis.
6. Skydelyje **GPS vieta** pirmas tinkamas GPS paketas sukuria žymeklį ir centruoja žemėlapį. Paslinkite žemėlapį arba pakeiskite mastelį. Naujos koordinatės keičia žymeklio vietą ir išlaiko jūsų pasirinktą žemėlapio vaizdą.
7. Skaitymo metu perjunkite **Tamsi tema**. Ryšys ir grafiko istorija išlieka. Programa įsimena temos pasirinkimą kitam paleidimui.
8. Spauskite **Atsijungti**. Duomenų srautas sustoja, o terminale atsiranda atsijungimo pranešimas.

## Žemėlapio plytelės

„Leaflet 1.9.4“ įkeliamas iš projekto išteklių. Žemėlapiui reikia interneto ryšio. Programa HTTPS ryšiu atsisiunčia matomas plyteles su programą identifikuojančia antrašte ir jas talpina vietinėje talpykloje pagal serverio atsakymo antraštes. Žymeklio koordinatės apdorojamos programoje, o plytelių serveriui siunčiami plytelių indeksai. Jei plytelės nepasiekiamos, GPS laukeliai, grafikas ir terminalas veikia toliau. [OpenStreetMap plytelių naudojimo taisyklės](https://operations.osmfoundation.org/policies/tiles/)

## Atitiktis LD2 aprašo 4–7 žingsniams

| Žingsnis | Patikra ir realizacija |
| --- | --- |
| 4 | Originalus LD1 paketas priimamas išorinėje terminalo programoje. Žr. bandymo komandą aukščiau. |
| 5 | jSerialComm išvardija prievadus ir fone skaito pasirinkto prievado duomenis. JavaFX terminalas rodo originalias eilutes. |
| 6 | JavaFX grafikas rodo LIS2DUXS12 AX, AY ir AZ pagreičius g vienetais. |
| 7 | Platuma, ilguma, EET laikas ir palydovų skaičius atnaujinami su kiekvienu pilnu GPS paketu. |

5–7 žingsnių funkcijos įgyvendintos Java ir JavaFX pagal pasirinktą projekto technologiją. Kompiuterio pusėje naudojami USART6 parametrai sutikrinti su LD1 `main.c` ir veikiančia `picocom` konfigūracija. LD1 TIM11 nustatytas maždaug 333,3 ms siuntimo periodas.

## Kodas ir automatinė patikra

- `serial` atidaro ir skaito nuoseklųjį prievadą atskiroje gijoje.
- `protocol` surenka CRLF ir LF eilutes, išskaido paketą ir konvertuoja laipsnius su minutėmis į dešimtainius laipsnius. Pietų ir vakarų koordinatės dekoduojamos su minuso ženklu.
- `ui` atnaujina JavaFX grafiką, 3D dėžutę, GPS laukelius bei žemėlapį, ryšio būseną ir terminalą.

Testai apima pateiktą paketą, neigiamą AY, S/W koordinates, dalimis gautą eilutę su perskelta CRLF pabaiga, kelis paketus viename nuskaityme, pasikartojančią EET sekundę ir skaitymo tęsimą po netinkamos eilutės. Pasvirimo testai tikrina abiejų ašių kryptis, triukšmo slopinimą ir perėjimą ties ±180°.
