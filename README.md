# 🔗 Encurtador de URL — AWS Lambda + S3

Encurtador de URLs serverless, construído com **Java 17 + Spring** e rodando inteiramente sobre a AWS: duas AWS Lambdas independentes cuidam da criação e do redirecionamento dos links, com o **Amazon S3** como camada de armazenamento.

Projeto de estudo focado em praticar arquitetura serverless na AWS, sem servidor fixo, sem banco de dados tradicional e com custo próximo de zero (free tier).

---

## 🖼️ Arquitetura

<img width="830" height="358" alt="image" src="https://github.com/user-attachments/assets/63cc4fb6-932e-4931-8372-031fc289a799" />

O fluxo é dividido em duas lambdas independentes, cada uma publicada como um projeto Java separado e empacotada em seu próprio `.jar`:

1. **CreateURLShortLambda** — recebe a URL original e o tempo de expiração, gera um código curto e salva os dados no S3.
2. **RedirectShortURLLambda** — recebe o código curto, busca os dados no S3 e redireciona para a URL original (ou retorna erro se o link expirou ou não existe).

Cada registro de URL é salvo como um arquivo `.json` no bucket, usando o código curto como chave (ex: `a1b2c3d4.json`).

> No diagrama a entrada é representada por uma camada de API genérica; na implementação atual, cada Lambda é exposta diretamente via **Lambda Function URL**, sem API Gateway.
---

## ⚙️ Como funciona

### 1. Encurtar uma URL

```
POST https://<create-lambda-url>/
Content-Type: application/json

{
  "originalUrl": "https://exemplo.com/pagina-bem-longa",
  "expirationTime": "24"
}
```

- `expirationTime` é o número de **horas** até o link expirar.
- A lambda gera um código aleatório de 8 caracteres (UUID truncado), calcula o timestamp absoluto de expiração e salva um objeto `{ originalUrl, expirationTime }` no S3, com o código como nome do arquivo.

**Resposta:**
```json
{ "code": "a1b2c3d4" }
```

### 2. Acessar a URL encurtada

```
GET https://<redirect-lambda-url>/a1b2c3d4
```

- A lambda busca `a1b2c3d4.json` no bucket.
- Se o link **ainda é válido**, responde com `302` e o header `Location` apontando para a URL original — o navegador redireciona automaticamente.
- Se o link **expirou**, responde `410 Gone`.
- Se o código **não existe**, responde `404 Not Found`.

---

## 🧱 Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot (autoconfig, sem servidor web embutido) |
| Compute | AWS Lambda (2 funções independentes) |
| Storage | Amazon S3 (armazenamento dos metadados em JSON) |
| Exposição | AWS Lambda Function URLs |
| Serialização | Jackson (`ObjectMapper`) |
| SDK AWS | AWS SDK v2 (`software.amazon.awssdk`) |
| Front-end | React + TypeScript + Tailwind CSS |

---

## 📁 Estrutura do projeto

O backend é dividido em **dois projetos Java separados**, cada um gerando seu próprio `.jar` de deploy — decisão feita porque cada Lambda tem responsabilidades e dependências distintas:

```
encurtador-url-aws/          # Lambda: cria e armazena URLs curtas
└── EncurtadorUrlAwsApplication.java

redirect-url-shortener/      # Lambda: busca e redireciona
└── Main.java

frontend/                    # Interface web (React + Tailwind)
└── UrlShortener.tsx
```

---

## 🚀 Rodando / fazendo deploy

1. Build de cada projeto backend com Maven:
   ```bash
   mvn clean package
   ```
2. Subir o `.jar` gerado como código de cada Lambda no console AWS (ou via CLI/CI).
3. Criar um bucket S3 e ajustar o nome no código (`url-shortner-storage-project`).
4. Dar permissão IAM às duas Lambdas para `s3:PutObject` (create) e `s3:GetObject` (redirect) no bucket.
5. Habilitar **Function URL** em cada Lambda, com CORS configurado para aceitar o domínio do front-end.
6. No front-end, apontar as constantes `SHORTENER_ENDPOINT` e `REDIRECT_BASE` para as URLs geradas.

---

## 🔮 Possíveis evoluções

- Migrar de S3 para **DynamoDB**, usando TTL nativo para expiração automática dos links.
- Colocar as Lambdas atrás de **API Gateway**, com usage plan e rate limiting.
- Infraestrutura como código (Terraform ou AWS SAM) em vez de configuração manual no console.
- Contador de cliques por link.
- Testes unitários (JUnit + Mockito) para os handlers.

---
