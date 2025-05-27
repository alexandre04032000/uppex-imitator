# Uppex

# Uppex: Parametric Generation for UPPAAL and IMITATOR Models

**Uppex** is a tool that automates the generation of UPPAAL (`.xml`) and IMITATOR (`.imi`) model variants using rules and configurations defined in a companion Excel file (`.xlsx`). This enables scalable and systematic parametric analysis through annotations and feature-based configurations.

---

## 📘 What Are UPPAAL and IMITATOR?

### 🕒 UPPAAL
- A tool for modeling, simulation, and formal verification of **real-time systems**.
- Uses **timed automata** to model concurrent system behavior.
- Allows verification of temporal properties such as:
  - Absence of *deadlocks*
  - Deadline satisfaction
  - State safety and reachability
- Input file format: `.xml`

### ⏱ IMITATOR
- A tool for **parametric timed verification**.
- Supports analysis with **symbolic timing parameters**.
- Enables parameter synthesis, robustness checking, and more.
- Input files: `.imi` (model), `.imiprop` (properties)

---

## ✨ How Uppex Works

Uppex reads:
- A model file (`.xml` or `.imi`)
- An Excel file (`.xlsx`) with the same base name

The Excel file contains instructions to:
- Modify annotated sections
- Inject or replace values
- Generate multiple configurations

### 🧩 Example: UPPAAL Annotation

```xml
// @myAnnotation
const int v1 = 1;
const int v2 = 2;
```

### 🧩 Example: IMITATOR Annotation

```pascal
(*@Limits*)
sessionTime = 100 : constant;
reactTime = 20 : constant;
```

---

## 📊 Excel Sheets Structure

### Annotation Sheet `@myAnnotation`

```plaintext
const $type $var = $number;
```

| var | type | num |
|-----|------|-----|
| v1  | int  | 10  |
| v2  | int  | 20  |

### Replacing `<queries>` Block in UPPAAL

Sheet named `<queries>` will override the XML `<queries>` block.

```xml
<query>
  <formula>$Formula</formula>
  <comment>$Comment</comment>
</query>
```

| Formula         | Comment                |
|----------------|------------------------|
| `A[]!deadlock` | No deadlocks           |
| `A[] W.Idle`   | The worker is always Idle |

---

## ⚙️ Supporting Multiple Configurations

### Configuration Sheet `@Configurations`

| Configuration | Feature1 | Feature2 |
|---------------|----------|----------|
| Main          |          |          |
| Conf2         | x        |          |
| Conf3         |          | x        |

Each configuration can enable one or more *features* which will conditionally apply certain annotation rules.

### Feature-Based Rules in Annotations

| var | type | num   | Features  |
|-----|------|--------|-----------|
| v1  | int  | 10000  | Feature1  |
| v1  | int  | 10     | Feature2  |
| v2  | int  | 20     |           |

> If multiple rules match the same variable, the **last applicable one** prevails.

---

## 🧪 IMITATOR-Specific Features

Annotations in IMITATOR models use `(*@Name*)`. Uppex enables:

- Replacing blocks (parameters, invariants, etc.)
- Creating `.imi` and `.imiprop` files per configuration
- Advanced feature expressions support:

| Expression Type           | Example                            |
|---------------------------|------------------------------------|
| Boolean logic             | `Lazy && !Overworker`              |
| String comparison         | `Count == ?`                       |
| Numeric comparison        | `Slow > 0.5`                       |
| Mixed expressions         | `(Count == ?) && (Slow < 10)`     |

---

## 🚀 How to Use

### 🛠 Prerequisites

- [SBT](https://www.scala-sbt.org) – for building the JAR
- [Docker](https://www.docker.com) – required at runtime
- JVM 1.8 or later
- [UPPAAL](https://uppaal.org) (optional, for property verification)

### 📥 Download or Build

**Download** the latest `.jar`:  
[https://github.com/cister-labs/uppex/releases](https://github.com/cister-labs/uppex/releases)

**Or build it manually:**
```bash
sbt assembly
```

### ▶️ Run

Apply the default configuration:
```bash
java -jar uppex.jar myfile.xlsx
```

Run all configurations and verify properties:
```bash
java -jar uppex.jar --runAll myfile.xlsx
```

> Requires `verifyta` in your `PATH` (UPPAAL's model checker)  
> Optional: Use `timeout` command for time-limited runs

---

## 📁 Examples

### ✅ Basic Example (UPPAAL)
Path: [`examples/simple`](https://github.com/cister-labs/uppex/blob/main/examples/simple)

- UPPAAL model + Excel annotations
- Includes both `@` (annotations) and `<queries>` (block replacements)
- Script: `runuppaal.command`

### ⚙️ Advanced Example with Configurations
Path: [`examples/simple-with-conf`](https://github.com/cister-labs/uppex/blob/main/examples/simple-with-conf)

- Multiple configurations via `@Configurations`
- Uses feature expressions in annotation tables

---

## 📚 Resources

- [UPPAAL Official Site](https://uppaal.org)
- [IMITATOR User Manual](https://www.imitator.fr/files/IMITATOR-user-manual.pdf)
- [Uppex GitHub Repository](https://github.com/cister-labs/uppex)




<!-- _This work is done by Infotiv AB under VALU3S project in a collaboration with RISE under ITEA3 European IVVES project. This project has received funding from the ECSEL Joint Undertaking (JU) under grant agreement No 876852. The JU receives support from the European Union’s Horizon 2020 research and innovation programme and Austria, Czech Republic, Germany, Ireland, Italy, Portugal, Spain, Sweden, Turkey._

_The ECSEL JU and the European Commission are not responsible for the content on this website or any use that may be made of the information it contains._
 -->

### Imitator

An example that illustrates the capabilities of Imitator can be found in the folder [examples/Test-Imitator](https://github.com/alexandre04032000/uppex-imitator/tree/b638950c9004c0fa47c24dd51c80ee260a9e46b7/examples/Teste_novo_excel) 
