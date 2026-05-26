# ia2-m2-sma

Simulacao de um sistema multiagente em JADE para aluguel de geradores com transporte fisico.

## Visao geral

O projeto inicia um ambiente JADE com:
- 1 agente coordenador de aluguel (`rental`)
- N agentes de transporte (`transport0..transportN-1`)
- M agentes consumidores (`consumer0..consumerM-1`)

Cada consumidor decide autonomamente sua necessidade (carga, duracao e orcamento), solicita ao `rental`, e recebe confirmacao ou falha.

## Tecnologias e requisitos

- Java (projeto configurado com `maven.compiler.release` 25)
- Maven
- JADE `4.6.0`

Dependencias e classe principal estao no `pom.xml`:
- Main class: `br.univali.cc.ia2.m2.sma.Main`

## Como executar

No diretorio raiz do projeto:

```bash
mvn clean compile exec:java
```

Ao iniciar:
- `Main` cria o container JADE principal.
- Sobe `rental` com parametro de quantidade de transportadoras.
- Sobe os agentes `transport`.
- Sobe os agentes `consumer`.

Valores atuais no `Main`:
- `numTransportes = 3`
- `numConsumidores = 4`

## Agentes e responsabilidades

### 1) `Main`

Responsavel por bootstrap do sistema:
- cria runtime/profile/container JADE;
- instancia os agentes;
- define quantidades iniciais de transportadoras e consumidores.

### 2) `ConsumerAgent`

Representa clientes autonomos que fazem pedidos ciclicos.

Comportamento:
- gera decisao local com base em urgencia:
  - urgencia baixa: carga ~2000-3999W
  - urgencia media: carga ~4000-6999W
  - urgencia alta: carga ~7000-10000W
- define duracao (1-12h) e orcamento maximo;
- envia `REQUEST` para `rental` com:
  - `id`, `consumer`, `load`, `duration`, `budget`
- aguarda resposta:
  - `INFORM`: pedido confirmado; espera mais tempo para novo pedido;
  - `FAILURE`: pedido falhou; tenta novamente mais rapido.

Caracteristicas importantes:
- cada consumidor usa seed (argumento opcional) para variacao de decisao;
- gera `conversationId` no formato `consumerX-req-Y`.

### 3) `RentalAgent`

Agente central de coordenacao de aluguel + selecao de transporte.

Regras de negocio:
- capacidade maxima do gerador: `10000W`;
- preco base aluguel: `R$80/h`;
- adicional para alta carga (>6000W): `R$50/h`;
- pequena variacao aleatoria no preco final de aluguel.

Fluxo interno:
1. recebe `REQUEST` do consumidor;
2. valida capacidade e orcamento minimo;
3. se viavel, inicia comportamento coordenador para consultar transportes;
4. escolhe melhor proposta de transporte que:
   - caiba no orcamento total (aluguel + transporte),
   - tenha menor preco de transporte;
5. envia `ACCEPT_PROPOSAL` para transportadora escolhida;
6. aguarda conclusao fisica (`INFORM`) da transportadora;
7. confirma ao consumidor com resumo final de custos.

Falhas tratadas:
- carga acima da capacidade -> `FAILURE`;
- aluguel sozinho acima do orcamento -> `FAILURE`;
- nenhuma transportadora viavel -> `FAILURE`;
- timeout para conclusao do transporte -> `FAILURE`.

Janelas de tempo no coordenador:
- ate ~2s para coletar propostas de transporte;
- ate ~12s para concluir transporte aceito.

### 4) `TransportAgent`

Simula transportadora fisica com disponibilidade exclusiva (um transporte por vez).

Comportamento em duas fases:
1. **Consulta (`REQUEST`)**  
   Recebe consulta do `rental` e responde:
   - `PROPOSE` com `vehicle`, `price`, `eta`, `totalMinutes`; ou
   - `REFUSE` se ja estiver ocupada.

2. **Aceite (`ACCEPT_PROPOSAL`)**  
   Ao receber aceite:
   - marca estado ocupado;
   - simula ida+volta com `WakerBehaviour`;
   - ao final envia `INFORM` ao `rental`;
   - volta ao estado disponivel.

Decisoes de veiculo por carga:
- ate 3000W: van leve;
- ate 6000W: caminhao medio;
- acima: caminhao pesado.

O tempo real de simulacao e acelerado por escala:
- `MILIS_POR_MINUTO_SIMULADO = 80`
- limite de duracao simulada por transporte: `12000ms`.

## Protocolo de mensagens (ACL)

### Consumidor -> Rental
- `REQUEST`
- Conteudo: `id`, `consumer`, `load`, `duration`, `budget`

### Rental -> Transport
- `REQUEST` (consulta de disponibilidade/proposta)
- `ACCEPT_PROPOSAL` (escolha da oferta vencedora)

### Transport -> Rental
- `PROPOSE` (oferta)
- `REFUSE` (ocupado/sem oferta valida)
- `INFORM` (transporte concluido)

### Rental -> Consumidor
- `INFORM` (servico confirmado, com custos e veiculo)
- `FAILURE` (motivo da falha)

Todas as etapas usam `conversationId` para correlacionar mensagens do mesmo pedido.

## Fluxo completo do sistema

1. `consumerX` decide autonomamente um pedido.
2. `consumerX` envia `REQUEST` para `rental`.
3. `rental` valida capacidade e preco de aluguel.
4. `rental` consulta todos os `transportY`.
5. cada `transportY` responde com `PROPOSE` ou `REFUSE`.
6. `rental` seleciona a melhor proposta dentro do orcamento total.
7. `rental` envia `ACCEPT_PROPOSAL` para a transportadora escolhida.
8. `transportY` executa transporte simulado (ida+volta).
9. `transportY` envia `INFORM` de conclusao para `rental`.
10. `rental` envia `INFORM` de confirmacao para `consumerX`.
11. consumidor aguarda e reinicia ciclo com novo pedido.

Se qualquer condicao falhar no caminho, o consumidor recebe `FAILURE` e tenta novamente.

## Observacoes de simulacao

- O sistema e estocastico (usa `Random`), entao resultados variam entre execucoes.
- Com varios consumidores e poucas transportadoras, podem ocorrer recusas por indisponibilidade.
- O comportamento de mercado emerge da competicao por transporte e limite de orcamento dos consumidores.
