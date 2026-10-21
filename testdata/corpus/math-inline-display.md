# Formulas in the text

Twenty formulas from simple to hard (C-14). Inline pairs sit inside sentences, display pairs stand alone, and a few displays are written over several lines or as a fenced `math` block. Every formula appears as display math at least once.

## Small ones

Einstein wrote $E = mc^2$ and the world changed. The Pythagorean relation $a^2 + b^2 = c^2$ is older, and a ratio is just $\frac{a}{b}$ when you squint. Indices can stack, as in $x_{i,j}^{2}$, which is why the markup needs braces.

$$E = mc^2$$

$$a^2 + b^2 = c^2$$

$$\frac{a}{b}$$

$$x_{i,j}^{2}$$

## Roots and Greek letters

A distance is $\sqrt{x^2+y^2}$ and a cube root of a ratio is $\sqrt[3]{\frac{a}{b}}$. Greek letters read as plain words in a sentence: $\alpha + \beta = \gamma$ is the shortest line in this section.

$$\sqrt{x^2+y^2}$$

$$\sqrt[3]{\frac{a}{b}}$$

$$\alpha + \beta = \gamma$$

## Sums, integrals and limits

The sum of the first n integers is $\sum_{i=1}^{n} i = \frac{n(n+1)}{2}$, and the limit $\lim_{x\to 0}\frac{\sin x}{x}=1$ is the first one every student meets.

$$\sum_{i=1}^{n} i = \frac{n(n+1)}{2}$$

$$\int_0^\infty e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}$$

$$\lim_{x\to 0}\frac{\sin x}{x}=1$$

The quadratic formula comes with a plus-minus sign and a root.

$$x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}$$

## Matrices, cases and alignment

A small matrix such as $\begin{pmatrix} a & b \\ c & d \end{pmatrix}$ fits in a line of text, but it is easier to read on its own.

$$\begin{pmatrix} a & b \\ c & d \end{pmatrix}$$

A piecewise function uses the cases environment, here over several lines.

$$
f(x)=\begin{cases}
x^2 & x\ge 0 \\
-x & x<0
\end{cases}
$$

Physics brings operators and vectors. The Schrodinger equation $\hat{H}\psi = i\hbar\frac{\partial}{\partial t}\psi$ can sit in a sentence, and the long ones stand alone.

$$\hat{H}\psi = i\hbar\frac{\partial}{\partial t}\psi$$

$$\prod_{p\ \text{prime}} \frac{1}{1-p^{-s}} = \sum_{n=1}^\infty \frac{1}{n^s}$$

$$\nabla\times\vec{B} = \mu_0\vec{J}+\mu_0\varepsilon_0\frac{\partial\vec{E}}{\partial t}$$

An aligned derivation, with line ends that look like hard breaks to a Markdown reader but are TeX row separators.

$$
\begin{aligned}
(a+b)^2 &= a^2+2ab+b^2 \\
(a-b)^2 &= a^2-2ab+b^2
\end{aligned}
$$

## Sets and the hardest two

The reals sit inside the complex numbers: $\mathbb{R}\subset\mathbb{C},\quad \forall \epsilon>0\ \exists\delta>0$ in one breath.

```math
\mathbb{R}\subset\mathbb{C},\quad \forall \epsilon>0\ \exists\delta>0
```

The Cauchy-Schwarz inequality scales its parentheses with left and right.

$$\left(\sum_{k=1}^{n} a_k b_k\right)^2 \le \left(\sum_{k=1}^{n} a_k^2\right)\left(\sum_{k=1}^{n} b_k^2\right)$$

A three by three determinant closes the set.

$$
\det\begin{vmatrix}
a_{11} & a_{12} & a_{13}\\
a_{21} & a_{22} & a_{23}\\
a_{31} & a_{32} & a_{33}
\end{vmatrix}
$$

## In containers

- A list item with display math:

  $$E = mc^2$$

- Another item with $\frac{a}{b}$ inline.

> A quote about $\alpha + \beta = \gamma$ and then
>
> $$\sqrt{x^2+y^2}$$

An escaped dollar sign is plain text: \$5 is a price, not a formula. A pair in the middle of prose, such as $$a^2 + b^2 = c^2$$ here, is read as inline math.
