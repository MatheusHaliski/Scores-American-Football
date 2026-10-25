/**
 * Mundo padrão do modo mock, derivado das amostras douradas (docs/validation/golden/career-calendar.json) e do
 * `DefaultWorldFactory` do backend: as 32 franquias da Liga Velion (2 conferências × 4 divisões) e o calendário de 18 semanas
 * (17 jogos + 1 bye por franquia). Gerado por script; não edite à mão.
 */
export const WORLD_NAME = "Mundo Padrão Scores AF";
export const LEAGUE_NAME = "Liga Velion de Futebol Americano";
export const LEAGUE_SHORT = "LVFA";
/** Teto e piso salarial da liga em centavos de S$ (como a API). */
export const SALARY_CAP = 25_500_000_000;
export const SALARY_FLOOR = 22_700_000_000;
export const SEASON_YEAR = 2026;
export const SEASON_START = "2026-09-10";
export const REGULAR_WEEKS = 18;
export const CONFERENCES = [
  { shortName: "CAT", name: "Conferência Atlântica", divisions: ["Atlântica Norte", "Atlântica Leste", "Atlântica Sul", "Atlântica Oeste"] },
  { shortName: "CPA", name: "Conferência Pacífica", divisions: ["Pacífica Norte", "Pacífica Leste", "Pacífica Sul", "Pacífica Oeste"] },
] as const;
export const KICKOFF_SLOTS = ["THU_NIGHT","SUN_EARLY","SUN_LATE","SUN_NIGHT","MON_NIGHT"] as const;
/** [sigla, nome, cidade, primária, secundária, destaque, conferência, divisão, prestígio] */
export const CLUBS: ReadonlyArray<readonly [string, string, string, string, string, string, string, string, number]> = [
  ["BSE","Arpões de Baía Serena","Baía Serena","#005B7F","#9AD1D4","#F4F4F4","CAT","Atlântica Norte",64],
  ["VAU","Carcarás de Vila Aurora","Vila Aurora","#3B1F5C","#F28C28","#FFFFFF","CAT","Atlântica Norte",58],
  ["SAL","Lobos-Guará de Serra Alta","Serra Alta","#7A2E1F","#E8C39E","#1A1A1A","CAT","Atlântica Norte",70],
  ["PAV","Trovões de Porto Alvorada","Porto Alvorada","#1B2A4A","#F2B705","#FFFFFF","CAT","Atlântica Norte",82],
  ["VSE","Bisões de Vale Sereno","Vale Sereno","#4E342E","#D7CCC8","#C62828","CAT","Atlântica Leste",68],
  ["RAM","Harpias de Rio Ametista","Rio Ametista","#5E2B97","#C0C0C0","#111111","CAT","Atlântica Leste",73],
  ["CCE","Jaguatiricas de Campo Celeste","Campo Celeste","#0D47A1","#FFCA28","#FFFFFF","CAT","Atlântica Leste",61],
  ["PLU","Tubarões-Martelo de Pedra Lume","Pedra Lume","#0E3B43","#57C4AD","#E6E6E6","CAT","Atlântica Leste",76],
  ["CVE","Centauros de Cerro Ventania","Cerro Ventania","#1B5E20","#FFD54F","#FFFFFF","CAT","Atlântica Sul",60],
  ["LPR","Furacões de Lagoa Prata","Lagoa Prata","#B71C1C","#ECEFF1","#212121","CAT","Atlântica Sul",66],
  ["PAN","Mamutes de Planalto Anil","Planalto Anil","#283593","#FF7043","#FFFFFF","CAT","Atlântica Sul",71],
  ["MAZ","Tempestades de Monte Azulão","Monte Azulão","#263238","#4FC3F7","#FFFFFF","CAT","Atlântica Sul",79],
  ["VAM","Avalanches de Vila Âmbar","Vila Âmbar","#37474F","#FFB300","#FFFFFF","CAT","Atlântica Oeste",57],
  ["BCO","Ciclones de Baía Coral","Baía Coral","#E65100","#263238","#FFFFFF","CAT","Atlântica Oeste",69],
  ["PBR","Cometas de Porto Brisa","Porto Brisa","#00695C","#FFAB00","#FFFFFF","CAT","Atlântica Oeste",84],
  ["SCO","Escorpiões de Serra Cobalto","Serra Cobalto","#1A237E","#B0BEC5","#D50000","CAT","Atlântica Oeste",63],
  ["AJA","Condores de Alto Jacarandá","Alto Jacarandá","#4A148C","#FFEB3B","#FFFFFF","CPA","Pacífica Norte",67],
  ["NES","Javalis de Nova Esmeralda","Nova Esmeralda","#004D40","#FFFFFF","#A1887F","CPA","Pacífica Norte",80],
  ["PGR","Sucuris de Ponta Granito","Ponta Granito","#33691E","#CDDC39","#000000","CPA","Pacífica Norte",74],
  ["CRU","Tamanduás de Campos Rubi","Campos Rubi","#880E4F","#F8BBD0","#FFFFFF","CPA","Pacífica Norte",62],
  ["SLU","Albatrozes de São Lucário","São Lucário","#01579B","#FFFFFF","#FF6F00","CPA","Pacífica Leste",77],
  ["BSP","Bigornas de Barra do Sol Poente","Barra do Sol Poente","#212121","#FF5722","#BDBDBD","CPA","Pacífica Leste",65],
  ["RTU","Faróis de Ribeira Turquesa","Ribeira Turquesa","#006064","#FFD600","#FFFFFF","CPA","Pacífica Leste",59],
  ["SBR","Vulcões de Santa Bruma","Santa Bruma","#BF360C","#212121","#FFC107","CPA","Pacífica Leste",72],
  ["CSA","Marujos de Costa Safira","Costa Safira","#0D47A1","#FFFFFF","#E53935","CPA","Pacífica Sul",75],
  ["PQU","Meteoros de Porto Quartzo","Porto Quartzo","#311B92","#00E5FF","#FFFFFF","CPA","Pacífica Sul",83],
  ["TVE","Pioneiros de Terra Ventura","Terra Ventura","#827717","#FFFFFF","#1B5E20","CPA","Pacífica Sul",66],
  ["CAL","Sentinelas de Cedro Alto","Cedro Alto","#3E2723","#8BC34A","#FFFFFF","CPA","Pacífica Sul",61],
  ["LFU","Arraias de Lagoa Funda","Lagoa Funda","#0277BD","#B3E5FC","#FFFFFF","CPA","Pacífica Oeste",78],
  ["ABL","Cascavéis de Areia Branca do Leste","Areia Branca do Leste","#5D4037","#FFE082","#000000","CPA","Pacífica Oeste",63],
  ["MCA","Rinocerontes de Morro Carmim","Morro Carmim","#8E0000","#9E9E9E","#FFFFFF","CPA","Pacífica Oeste",70],
  ["VTO","Tatus-Canastra de Vila Topázio","Vila Topázio","#F57F17","#3E2723","#FFFFFF","CPA","Pacífica Oeste",56],
];
/** "semana.mandante.visitante.diasDesdeOInícioDaSemana.slot" (índices em CLUBS e KICKOFF_SLOTS). */
export const FIXTURES = "1.1.3.0.0 1.20.21.3.1 1.12.15.3.1 1.13.14.3.1 1.5.6.3.1 1.24.26.3.1 1.7.4.3.1 1.23.22.3.1 1.29.28.3.2 1.16.18.3.2 1.17.19.3.2 1.10.9.3.2 1.25.27.3.2 1.11.8.3.2 1.30.31.3.3 1.0.2.4.4 2.20.23.0.0 2.15.14.3.1 2.6.4.3.1 2.2.1.3.1 2.24.25.3.1 2.26.27.3.1 2.19.18.3.1 2.3.0.3.1 2.28.30.3.2 2.21.22.3.2 2.29.31.3.2 2.16.17.3.2 2.10.11.3.2 2.7.5.3.2 2.9.8.3.3 2.13.12.4.4 3.25.26.0.0 3.12.14.3.1 3.1.0.3.1 3.29.30.3.1 3.13.15.3.1 3.22.20.3.1 3.31.28.3.1 3.11.9.3.1 3.21.23.3.2 3.5.4.3.2 3.6.7.3.2 3.17.18.3.2 3.10.8.3.2 3.19.16.3.2 3.24.27.3.3 3.2.3.4.4 4.8.21.0.0 4.12.13.3.1 4.14.6.3.1 4.2.0.3.1 4.30.1.3.1 4.27.25.3.1 4.7.3.3.1 4.23.18.3.1 4.20.19.3.2 4.28.26.3.2 4.4.17.3.2 4.29.24.3.2 4.16.5.3.2 4.11.10.3.2 4.31.22.3.3 4.15.9.4.4 5.0.26.0.0 5.20.22.3.1 5.28.31.3.1 5.4.7.3.1 5.15.13.3.1 5.17.16.3.1 5.18.30.3.1 5.12.27.3.2 5.1.11.3.2 5.14.24.3.2 5.9.2.3.2 5.10.3.3.2 5.8.6.3.3 5.29.21.4.4 6.22.12.0.0 6.8.9.3.1 6.6.5.3.1 6.25.30.3.1 6.27.24.3.1 6.3.11.3.1 6.23.21.3.1 6.0.31.3.2 6.15.7.3.2 6.2.28.3.2 6.18.10.3.2 6.19.17.3.2 6.26.14.3.3 6.13.4.4.4 7.0.3.0.0 7.28.1.3.1 7.8.11.3.1 7.16.19.3.1 7.9.23.3.1 7.17.6.3.1 7.26.12.3.1 7.21.15.3.2 7.4.14.3.2 7.2.5.3.2 7.25.20.3.2 7.18.24.3.2 7.27.30.3.3 7.31.29.4.4 8.27.19.0.0 8.20.28.3.1 8.9.10.3.1 8.6.31.3.1 8.26.13.3.1 8.11.7.3.1 8.3.2.3.1 8.4.5.3.2 8.1.8.3.2 8.22.21.3.2 8.24.23.3.2 8.25.15.3.2 8.18.16.3.2 8.14.12.3.3 8.29.0.4.4 9.12.7.0.0 9.29.16.3.1 9.8.22.3.1 9.9.1.3.1 9.17.23.3.1 9.24.15.3.1 9.26.30.3.1 9.21.20.3.2 9.6.18.3.2 9.10.13.3.2 9.19.4.3.2 9.3.25.3.2 9.5.14.3.3 9.28.27.4.4 10.31.2.0.0 10.21.18.3.1 10.13.6.3.1 10.15.26.3.1 10.10.20.3.1 10.25.28.3.1 10.30.29.3.1 10.0.1.3.2 10.22.27.3.2 10.5.7.3.2 10.11.17.3.2 10.3.14.3.2 10.23.19.3.2 10.24.12.3.3 10.9.16.4.4 11.19.5.0.0 11.20.8.3.1 11.4.29.3.1 11.14.7.3.1 11.26.25.3.1 11.27.13.3.1 11.23.11.3.1 11.0.10.3.2 11.1.2.3.2 11.16.22.3.2 11.17.21.3.2 11.3.31.3.2 11.6.12.3.3 11.30.24.4.4 12.14.25.0.0 12.29.3.3.1 12.16.6.3.1 12.22.17.3.1 12.10.5.3.1 12.19.21.3.1 12.31.26.3.1 12.20.18.3.2 12.4.15.3.2 12.1.12.3.2 12.30.28.3.2 12.11.2.3.2 12.7.13.3.2 12.0.9.3.3 12.23.8.4.4 13.4.16.0.0 13.21.11.3.1 13.17.25.3.1 13.2.10.3.1 13.30.0.3.1 13.18.5.3.1 13.23.20.3.1 13.13.24.3.2 13.15.6.3.2 13.22.19.3.2 13.9.3.3.2 13.27.29.3.2 13.31.1.3.2 13.8.12.3.3 13.28.7.4.4 14.10.1.0.0 14.20.9.3.1 14.29.2.3.1 14.16.7.3.1 14.5.13.3.1 14.26.24.3.1 14.18.4.3.1 14.28.17.3.2 14.15.0.3.2 14.30.23.3.2 14.19.8.3.2 14.31.25.3.2 14.11.22.3.3 14.27.14.4.4 15.23.16.0.0 15.13.25.3.1 15.15.27.3.1 15.24.28.3.1 15.26.29.3.1 15.30.2.3.1 15.19.31.3.1 15.3.1.3.1 15.8.10.3.2 15.14.20.3.2 15.9.21.3.2 15.5.17.3.2 15.11.0.3.2 15.7.6.3.2 15.12.4.3.3 15.18.22.4.4 16.17.20.0.0 16.4.9.3.1 16.1.27.3.1 16.14.11.3.1 16.16.26.3.1 16.24.31.3.1 16.25.29.3.1 16.7.18.3.1 16.8.0.3.2 16.13.2.3.2 16.15.12.3.2 16.5.30.3.2 16.6.19.3.2 16.3.28.3.2 16.22.23.3.3 16.21.10.4.4 17.4.6.0.0 17.12.5.3.1 17.21.26.3.1 17.14.15.3.1 17.9.11.3.1 17.2.8.3.1 17.25.24.3.1 17.31.27.3.1 17.20.16.3.2 17.0.28.3.2 17.1.29.3.2 17.13.23.3.2 17.18.19.3.2 17.7.17.3.2 17.30.3.3.3 17.10.22.4.4 18.11.20.0.0 18.0.4.3.1 18.12.25.3.1 18.14.13.3.1 18.6.1.3.1 18.2.24.3.1 18.27.26.3.1 18.18.17.3.1 18.28.29.3.2 18.8.3.3.2 18.16.21.3.2 18.22.9.3.2 18.7.19.3.2 18.23.10.3.2 18.5.15.3.3 18.31.30.4.4";
