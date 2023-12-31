# egk mixnet maths

Preliminary explorations of mixnet implementations to be used with the ElectionGuard Kotlin library.

We use the ElectionGuard Kotlin library [7] for all the cryptography primitives. This library closely follows the ElectionGuard 2.0 specification [1].

Some of the prototype code in egk-mixlib is a port of code found in the OpenCHVote repository [8], and the appropriate license has been added. Please use any of this work in any way consistent with that.

The math here mostly recapitulates the work of Haenni et. al. [2], [3] in explaining the Terelius / Wikström (TW) mixnet algorithm [4], [5], and the work of Haines [9] that gives a formal proof of security of TW when the shuffle involves vectors of ciphertexts.

Ive tried to avoid notation that is hard to read, preferring for example, multiple character symbols like $pr$ instead of  r̃ or r̂ , since the glyphs can get too small to read when they are used in exponents or subscripts, and can be hard to replicate in places other than high quality Tex or PDF renderers.

**Table of Contents**

[TOC]

### Definitions

#### 1. The ElectionGuard Group

- $ \Z = \{. . . , −3, −2, −1, 0, 1, 2, 3, . . . \} $ is the set of integers.

- $ \Z_n  = \{0, 1, 2, . . . , n − 1\} $ is the ring of integers modulo n.
- $ \Z_n^* $ is the multiplicative subgroup of $ \Z_n$ that consists of all invertible elements modulo n. When p is a prime,  $ \Z_p^* = \{1, 2, 3, . . . , p − 1\} $
-  $ \Z_p^r $ is the set of r-th-residues in $\Z_p^* $ . Formally, $ \Z_p^r = \{y \in \Z_p^* $ for which there exists $x \in \Z_p^*$ where $y = x^r$ mod p}. When p is a prime for which p − 1 = q * r with q a prime that is not a divisor of the integer r, then  $\Z_p^r$ is an order-q cyclic subgroup of $\Z_p^*$ , and for any $y \in \Z_p^* $ , $y \in \Z_p^r $ if and only if $y^q$ mod p = 1.

We use the ElectionGuard Kotlin library [7] and ElectionGuard 2.0 specification [1] for all the cryptography primitives, in particular the parameters for $ \Z_p^r $, the variant of ElGamal encryption described next, and the use of HMAC-SHA-256 for hashing. 




#### 2. Permutations

A *permutation* is a bijective map $\psi: {1..N} \to {1..N}$. We use **px** to mean the permutation of a vector **x**, **px** = $\psi(\textbf x)$, so that $x_i$ = $px_j$, where $i={\psi(j)}$ and $j={\psi^{-1}(i)}$.   $x_i = px_{\psi^{-1}(i)}$,   $px_j = x_{\psi(j)}$, 

A *permutation* $\psi$ has a *permutation matrix* $B_\psi$ , where $b_{ij}$ = 1 if $\psi(i)$ = j, otherwise 0. Note that $\psi(\textbf x)$ = **px** = B**x** (matrix multiply).

If $B_\psi$ = ($b_{ij}$) is an N -by-N matrix over $\Z_q$ and **x** = $(x_1 , ..., x_N)$  a vector of N independent variables, then $B_\psi$ is a permutation matrix if and only
$$
\sum_{i=1}^n b_{ij} = 1\ \ \ \ (Condition\ 1) \\
\sum_{i=1}^n \sum_{j=1}^n b_{ij} x_i = \sum_{i=1}^n x_i \ \ \ \ (Condition\ 2) \\
$$



#### 3. Pedersen Commitments

For a set of messages $\textbf m = (m_1 .. m_n) \in \Zeta_q$, the *Extended Pedersen committment* to $\textbf m$ is
$$
\begin{align}
Commit(\textbf m, cr) = g^{cr} * h_1^{m_1} * h_2^{m_2} * .. h_n^{m_n} 
= g^{cr} * \prod_{i=1}^n h_i^{m_i}
\end{align}
$$
where ($ g, \textbf h $) are generators of  $ \Z_p^r $ with randomization nonce $ cr \in Z_q $.



If $\textbf b_i$ is the $i^{th}$ column of $B_\psi$, then the *permutation commitment to $\psi$* is defined as the vector of committments to its columns:
$$
Commit(\psi, \textbf {cr}) = (Commit(\textbf b_1, cr_1), Commit(\textbf b_2, cr_2),..Commit(\textbf b_N, cr_N)) =
$$
where
$$
\begin{align}
c_j = Commit(\textbf b_j, cr_j) = g^{cr_j} * \prod_{i=1}^n h_i^{b_{ij}} = g^{cr_j} * h_i ,\ for\ i=ψ^{-1}(j)
\end{align}
$$



####  4. ElGamal Encryption and Reencryption

$$
\begin{align}
(2a) \\
    Encr(m, \xi) = (g^{\xi}, K^{m+\xi}) = (a, b) \\
    Encr(0, \xi') = (g^{\xi'}, K^{\xi'}) \\
    \\
(2b)    \\
    (a, b)*(a',b') = (a*a', b*b') \\
    Encr(m, \xi) * Encr(m', \xi') = (g^{\xi+\xi'}, K^{m+m'+\xi+\xi'}) = Encr(m+m', \xi+\xi')\\
    \\
(2c)    \\
    (a, b)^k = (a^k, b^k) \\
    Encr(m, \xi)^k = (g^{\xi*k}, K^{(m*k+\xi*k)}) = Encr(m*k, \xi*k) \\
    \\
(2d)   \\
    \prod_{j=1}^n Encr(m_j, \xi_j) = (g^{\sum_{j=1}^n \xi_j}, K^{\sum_{j=1}^n m_j+ \sum_{j=1}^n \xi_j})
    = Encr(\sum_{j=1}^n m_j,\sum_{j=1}^n \xi_j) \\
    \prod_{j=1}^n Encr(m_j, \xi_j)^{k_j} = Encr(\sum_{j=1}^n (m_j*k_j),\sum_{j=1}^n (\xi_j*k_j)) \\
    \\
(2e)     \\
    ReEncr(m, r) = (g^{\xi+r}, K^{m+\xi+r}) = Encr(0, r) * Encr(m, \xi) \\
    ReEncr(m, r)^k = Encr(0, r*k) * Encr(m*k, \xi*k) \\
    \\
(2f)    \\
    \prod_{j=1}^n ReEncr(e_j, r_j)= (g^{\sum_{j=1}^n (\xi_j+r_j)}, K^{\sum_{j=1}^n (m_j+\xi_j+r_j)}) \\
    =  ReEncr(\prod_{j=1}^n e_j, \sum_{j=1}^n r_j) \\
(2e)    \\
    \prod_{j=1}^n ReEncr(m_j, r_j)^{k_j} = \prod_{j=1}^n Encr(0, r_j*k_j) * \prod_{j=1}^n Encr(m_j*k_j, \xi_j*k_j) \\
    = Encr(0,\sum_{j=1}^n (r_j*k_j)) * \prod_{j=1}^n Encr(m_j, \xi_j)^{k_j} \\
\end{align}
$$

Let 

1. ​	$e_j = Encr(m_j, \xi_j)$ 
2. ​	$re_j = ReEncr(m_j,r_j) = ReEncr(e_j,r_j) = Encr(0,r_j) * e_j$

Then
$$
\begin{align}
re_j &= Encr(0,r_j) * e_j \\
\prod_{j=1}^n re_j^{k_j} &= \prod_{j=1}^n Encr(0,r_j)^{k_j} * \prod_{j=1}^n e_j^{k_j} \\
&= Encr(0,\sum_{j=1}^n (r_j*k_j)) * \prod_{j=1}^n e_j^{k_j},\ \ \ \ (Equation\ 1) \\
\end{align}
$$

### ChVote

This follows Haenni et. al. [2], which has a good explanation of TW, sans vectors.

#### 1. Proof of permutation

Let **c** = $Commit(\psi, \textbf r)$ = $(c_1, c_2, .. c_N)$, with randomization vector **cr** = $(cr_1, cr_2, .. cr_N)$, and $crbar = \sum_{i=1}^n cr_i$. 

$Condition$ 1 implies that
$$
\prod_{j=1}^n c_j = \prod_{j=1}^n g^{cr_j} \prod_{i=1}^n h_i^{b_{ij}} = g^{crbar} \prod_{i=1}^n h_i\ = Commit(\textbf 1, crbar).\ \ \ (5.2)
$$

Let $\textbf u = (u_1 .. u_n)$ be arbitrary values  $\in \Zeta_q, \textbf {pu}$ its permutation by $\psi$, and  $cru=\sum_{j=1}^N {cr_j u_j}$.

 $Condition$ 2 implies that:
$$
\prod_{i=1}^n u_i = \prod_{j=1}^n pu_j\ \ \ (5.3)
$$

$$
\prod_{j=1}^n c_j^{u_j} = \prod_{j=1}^n (g^{cr_j} \prod_{i=1}^n h_i^{b_{ij}})^{u_j} = g^{cru} \prod_{i=1}^n h_i^{pu_i}\ = Commit(\textbf {pu}, cru)\ \ \ (5.4)
$$

Which constitutes proof that condition 1 and 2 are true, so that c is a commitment to a permutation matrix.



#### 2. Proof of equal exponents

Let $\textbf m$ be a vector of messages, $\textbf e$ their encryptions **e** = Encr($\textbf m$), and **re(e, r)** their reenryptions with nonces **r**.  A shuffle operation both reencrypts and permutes, so $shuffle(\textbf{e}, \textbf{r}) \to (\textbf{pre}, \textbf{pr})$, where **pre** is the permutation of **re ** by $\psi$, and **pr** the permutation of **r ** by $\psi$.
$$
re_i = ReEncr(e_i, r_i) =  Encr(0, r_i) * e_i \\
$$

$$
pre_j = ReEncr(pe_j, pr_j) =  Encr(0,pr_j) * e_j \\
$$


Let **u** be arbitrary values $\in \Z_q$ (to be specified later) and **pu** its permutation.

If the shuffle is valid, then it follows from $Equation\ 1$ above that


$$
\begin{align}
\prod_{j=1}^n pre_j^{pu_j} &= \prod_{j=1}^n (Encr(0,pr_j) * e_j)^{pu_j} \\
&= Encr(0,\sum_{j=1}^n (pr_j*pu_j)) * \prod_{j=1}^n e_j^{pu_j} \ \ \ \ (Equation\ 1)\\
&= Encr(0,sumru) * \prod_{j=1}^n e_j^{pu_j} \\
\end{align}
$$
where $sumru = \sum_{j=1}^n (pr_j*pu_j)$.



However, $e_j^{pu_j} = e_i^{u_i}$ for some i, so $\prod_{j=1}^n e_j^{pu_j} = \prod_{i=1}^n e_i^{u_j}$, and we have:
$$
\prod_{j=1}^n pre_j^{pu_j} = Encr(0,sumru) * \prod_{i=1}^n e_i^{u_i}\ \ \ (5.5)
$$



**Note** that (5.5) from [2] and line 141 of the code in *GenShuffleProof* in [8] has
$$
Encr(1,\tilde r),\ where\ \tilde r  = \sum_{j=1}^n pr_j * u_j
$$
whereas we have
$$
Encr(0,\tilde r),\ where\ \tilde r  = \sum_{j=1}^n pr_j * pu_j
$$

The $Encr(0, ..)$ is because we use exponential ElGamal, so is fine. Their use of $u_j$ instead of $pu_j$ appears to be a mistake. Its also possible there is a difference in notation that I didnt catch.

### Verificatum

#### ShuffleProof

$\vec{w}$ = ()

$\vec{pw}$ = Rencr(w)

$\alpha, \vec{\epsilon}$ random elements in $\Z_q$



**Create Committment:**
$$
\begin{align}
B &= \\
A^\prime &= g^\alpha \prod_{i=0}^{n-1} h_i^{eps_i} \\
F^\prime &= Encr(0, -\phi) \prod_{i=0}^{n-1} pw_i^{\epsilon_i} \\
\end{align}
$$
**Send challenge v and get Reply:**
$$
\begin{align}
k_A &= (r \cdot e) * v + \alpha \\
\vec{k_{E}} &= \vec{e^\prime} \cdot v + \vec{\epsilon} \\
k_E &= (rnonces \cdot e^{\prime}) * v + \alpha \\
\end{align}
$$



**Verify**
$$
\begin{align}
A^v \cdot A^\prime &= g^{k_A} \prod_{i=0}^{n-1} h_i^{k_{E,i}} \\
\end{align}
$$

### Shuffling vectors

#### Simple

Much of the literature assumes that each row to be mixed consists of a single ciphertext. In our application we need the possibility that each row consists of a vector of ciphertexts. So for each row i, we now have a vector of *w = width* ciphertexts:
$$
\textbf {e}_i = (e_{i,1},.. e_{i,w}) = \{e_{i,k}\},\ k=1..w
$$
The main work is to modify the proof of equal exponents for this case.

Suppose we are looking for the simplest generalization of 5.5:
$$
\prod_{j=1}^n pre_j^{pu_j} = Encr(0,sumru) \cdot \prod_{i=1}^n e_i^{u_i}\ \ \ (5.5)
$$
one could use the same nonce for all the ciphertexts in each row when reencrypting:
$$
\textbf r = \{r_j\}, j=1..n \\
re_{j,k} = ReEncr(e_{j,k}, r_j) =  Encr(0,r_j) \cdot e_{j,k}\ \ \ (case 1) \\
$$
or generate N = nrows * width nonces, one for each ciphertext:
$$
\textbf r = \{r_{j,k}\},\ j=1..n,\ k=1..w \\
re_{j,k} = ReEncr(e_{j,k}, r_{j,k}) =  Encr(0,r_{j,k}) \cdot e_{j,k}\ \ \ (case 2)
$$

Then eq 5.5 is changed to
$$
\prod_{j=1}^n \prod_{k=1}^w pre_{j,k}^{pu_j} = Encr(0,sumru') * \prod_{i=1}^n \prod_{k=1}^w e_{i,k}^{u_i}
$$
where, now 
$$
sumru' &= \sum_{j=1}^n width * (pr_j*pu_j)\ \ \ (case 1) \\
&= \sum_{j=1}^n \sum_{k=1}^n (pr_{j,k}*pu_j)\ \ \ (case 2).
$$

In algorithms 8.4, 8.5 of [2], the challenge includes a list of all the ciphertexts and their reencryptions in their hash function:
$$
\textbf u = Hash(..., \textbf e, \textbf {pe}, pcommit, pkq, i, ...)
$$
​	Here we just flatten the list of lists of ciphertexts for $\textbf e, \textbf {pe}$, so that all are included in the hash. Since the hash is dependent on the ordering of the hash elements, this should preclude an attack that switches ciphertexts within a row.

#### Verificatum

####  Haines Proof of vector shuffling

Haines [9]  gives a formal proof of security of TW when the shuffle involves vectors of ciphertexts.

We will use the notation above for case 2, using a separate nonce for each ciphertext:
$$
\textbf r = \{r_{j,k}\},\ j=1..n,\ k=1..w \\
re_{j,k} = ReEncr(e_{j,k}, r_{j,k}) =  Encr(0,r_{j,k}) \cdot e_{j,k}\ \ \ (case 2)
$$

This gives an nrows x width matrix R of reencryption nonces. The vector notation is a shorthand for component-wise operations:
$$
R = (\textbf r_1,..\textbf r_n) \\
Encr(\textbf e_i) = (Encr(e_{i,1}),..Encr(e_{i,w})) \\
ReEncr(\textbf e_i, \textbf r_i) = (ReEncr(e_{i,1}, r_{i,1}),..ReEncr(e_{i,1}, r_{i,w}))
$$
so now we have vector equations for rencryption:
$$
\textbf {re}_i = ReEncr(\textbf e_i, \textbf r_i) =  Encr(0, \textbf r_i) * \textbf e_i \\
$$
and the permuted form, as is returned by the shuffle:
$$
\textbf {pre}_j = ReEncr(\textbf {pe}_j, \textbf{pr}_j) =  Encr(0, \textbf {pr}_j) * \textbf e_j \\
$$

which corresponds to ntnu equation (p 3) of [9]:
$$
\textbf e^\prime_i = ReEnc(\textbf e_{π(i)}, R_{π(i)} ), π = π_M
$$

Let **ω** be width random nonces, **ω'** = permuted **ω**, and $\textbf {pe}_i$ = permuted $\textbf e_i = \textbf e^\prime_i$ as before. Then the $t_4$ equation (p 3, paragraph 2 of [9])  is a vector of  width components:

$$
\textbf t_4 &= ReEnc(\prod_i^n \textbf {pe}_i^{\textbf ω^\prime_i}, − \textbf {ω}_4 ) \\
 &= (ReEnc(\prod_i^n \textbf {pe}_i^{\textbf ω^\prime_i}, − \textbf {ω}_{4,1} ),..
 (ReEnc(\prod_i^n \textbf {pe}_i^{\textbf ω^\prime_i}, − \textbf {ω}_{4,w} )) \\
$$

where
$$
\prod_i^n \textbf {pe}_i^{\textbf ω^\prime_i}
$$
must be the product over  rows of the $k_{th}$ ciphertext in each row:
$$
(\prod_i^n \textbf {pe}_{i,1}^{\textbf ω^\prime_i},.. \prod_i^n \textbf {pe}_{i,w}^{\textbf ω^\prime_i}) \\
= \{\prod_i^n \textbf {pe}_{i,k}^{\textbf ω^\prime_i}\}, k = 1.. width \\
\textbf t_4 = \{ Rencr( \prod_i^n \textbf {pe}_{i,k}^{\textbf ω^\prime_i}, − \textbf {ω}_4 ) \}, k = 1.. width
$$

(quite a bit more complicated than "our simplest thing to do" above)



**extra**

to go back to (2f) and unravel this:
$$
\prod_{j=1}^n ReEncr(e_j, r_j) =  ReEncr(\prod_{j=1}^n e_j, \sum_{j=1}^n r_j)\ \ \ (2f) \\
\prod_{j=1}^n ReEncr(\textbf {pe}_i^{\textbf ω^\prime_i}, r_j) =  ReEncr(\prod_{j=1}^n \textbf {pe}_i^{\textbf ω^\prime_i}, \sum_{j=1}^n r_j)
$$





### Timings (preliminary)

#### 1. Operation counts

- *n* = number of rows, eg ballots or contests
- *width* = number of ciphertexts per row
- *N* = nrows * width = total number of ciphertexts to be mixed

**multi**

|                  | shuffle | proof of shuffle      | proof of exp  | verify          |
| ---------------- | ------- | --------------------- | ------------- | --------------- |
| regular exps     | 0       | 4 * n                 | 2 * width * n | 4*N + 4n + 4    |
| accelerated exps | 2 * N   | 3 * n + 2 * width + 4 | 0             | n + 2*width + 3 |

Even though N dominates, width is bound but nrows can get arbitrarily big. Could parallelize over the rows also. 

Could break into batches of 100 ballots each and do each batch in parallel. The advantage here is that there would be complete parallelization.

- exp is about 3 times slower after the acceleration cache warms up:

```
acc took 15288 msec for 20000 = 0.7644 msec per acc
exp took 46018 msec for 20000 = 2.3009 msec per exp
exp/acc took 3.01007326007326
```





#### 2. wallclock times (vmn/ch)

```
shuffle: took 5967 msecs = 1.755 msecs/text (3400 texts)
  proof: took 19248 msecs = 5.661 msecs/text (3400 texts)
 verify: took 34751 msecs = 10.22 msecs/text (3400 texts)
  total: took 59966 msecs = 17.63 msecs/text (3400 texts)
```

```
shuffle
nrows=100, width= 100 per row, N=10000, nthreads=32/16/8/4/2/1
took 1882 msecs = .1882 msecs/text (10000 texts) = 1882.0 msecs/shuffle for 1 shuffles
took 1880 msecs = .188 msecs/text (10000 texts) = 1880.0 msecs/shuffle for 1 shuffles
took 2550 msecs = .255 msecs/text (10000 texts) = 2550.0 msecs/shuffle for 1 shuffles
took 4624 msecs = .4624 msecs/text (10000 texts) = 4624.0 msecs/shuffle for 1 shuffles
took 8713 msecs = .8713 msecs/text (10000 texts) = 8713.0 msecs/shuffle for 1 shuffles
took 16446 msecs = 1.644 msecs/text (10000 texts) = 16446 msecs/shuffle for 1 shuffles
```

```
shuffle
nrows=100, width= 100 per row, N=10000, nthreads=32/24/16/8/4/2/1
took 1884 msecs = .1884 msecs/text (10000 texts) = 1884.0 msecs/shuffle for 1 shuffles
took 1853 msecs = .1853 msecs/text (10000 texts) = 1853.0 msecs/shuffle for 1 shuffles
took 1832 msecs = .1832 msecs/text (10000 texts) = 1832.0 msecs/shuffle for 1 shuffles
took 2564 msecs = .2564 msecs/text (10000 texts) = 2564.0 msecs/shuffle for 1 shuffles
took 4555 msecs = .4555 msecs/text (10000 texts) = 4555.0 msecs/shuffle for 1 shuffles
took 8844 msecs = .8844 msecs/text (10000 texts) = 8844.0 msecs/shuffle for 1 shuffles
took 17188 msecs = 1.718 msecs/text (10000 texts) = 17188 msecs/shuffle for 1 shuffles
```





#### 3. wallclock times (vmn/ch)

**ChVote**

|                  | shuffle | proof       | verify          |
| ---------------- | ------- | ----------- | --------------- |
| regular exps     | 0       | 2*N + 5*n   | 4*N + 4*n + 6   |
| accelerated exps | 2 * N   | 2*N + 3*n   | 8               |


nrows = 100, width = 34, N=3400

```
Time verificatum as used by rave

RunMixnet elapsed time = 27831 msecs
RunMixnet elapsed time = 26464 msecs)
RunMixnetVerifier elapsed time = 12123 msecs
RunMixnetVerifier elapsed time = 12893 msecs

total = 79.311 secs
```

```
Time egk-mixnet

  shuffle1 took 5505
  shuffleProof1 took 17592
  shuffleVerify1 took 33355
  shuffle2 took 5400
  shuffleProof2 took 17213
  shuffleVerify1 took 33446
  
  total: 119.711 secs, N=3400 perN=35 msecs
```

Vmn proof 27/(17.4+5.4) = 1.18 is 18% slower

Vmn has verifier 33355/12123 = 2.75 faster, TODO: investigate if theres an algorithm improvement there. Possibly related to the "wide integer" representation, eg see 

```
LargeInteger.modPowProd(LargeInteger[] bases, LargeInteger[] exponents, LargeInteger modulus)
```

More likely there are parallelization being done, eg in the same  routine. So to compare, we have to run vmn and see what parelization it gets. 

Also note LargeInteger.magic that allows use of VMGJ.

Vmn in pure Java mode, using BigInteger. TODO: Find out how much speedup using VMGJ gets.

SO why doesnt same speedup alply to proof?



**Parallelize egk-mixnet**

After parallelizing all sections of egk-mixnet that are O(N)  (time is in msecs):

| N    | shuffle1 | proof1 | verify1 | shuffle2 | proof2 | verify2 | total  |
| ---- | -------- | ------ | ------- | -------- | ------ | ------- | ------ |
| 1    | 5490     | 17315  | 33348   | 5501     | 17260  | 33277   | 118576 |
| 2    | 2872     | 9756   | 17932   | 2928     | 9725   | 17804   | 67640  |
| 4    | 1625     | 5746   | 10192   | 1546     | 5869   | 10282   | 41948  |
| 8    | 883      | 3774   | 6300    | 862      | 3867   | 6264    | 28592  |
| 16   | 693      | 2951   | 3993    | 659      | 2615   | 4119    | 22143  |

Could parallelize over the rows also. 

Could break into batches of 100 ballots each and do each batch in parallel. The advantage here is that there would be complete parallelization.



### References

1. Josh Benaloh and Michael Naehrig, *ElectionGuard Design Specification, Version 2.0.0*, Microsoft Research, August 18, 2023, https://github.com/microsoft/electionguard/releases/download/v2.0/EG_Spec_2_0.pdf 
2. Rolf Haenni, Reto E. Koenig, Philipp Locher, Eric Dubuis. *CHVote Protocol Specification Version 3.5*, Bern University of Applied Sciences, February 28th, 2023, https://eprint.iacr.org/2017/325.pdf
3. R. Haenni, P. Locher, R. E. Koenig, and E. Dubuis. *Pseudo-code algorithms for verifiable re-encryption mix-nets*. In M. Brenner, K. Rohloff, J. Bonneau, A. Miller, P. Y. A.Ryan, V. Teague, A. Bracciali, M. Sala, F. Pintore, and M. Jakobsson, editors, FC’17, 21st International Conference on Financial Cryptography, LNCS 10323, pages 370–384, Silema, Malta, 2017.
4. B. Terelius and D. Wikström. *Proofs of restricted shuffles*, In D. J. Bernstein and T. Lange, editors, AFRICACRYPT’10, 3rd International Conference on Cryptology inAfrica, LNCS 6055, pages 100–113, Stellenbosch, South Africa, 2010.
5. D. Wikström. *A commitment-consistent proof of a shuffle.* In C. Boyd and J. González Nieto, editors, ACISP’09, 14th Australasian Conference on Information Security and Privacy, LNCS 5594, pages 407–421, Brisbane, Australia, 2009.
6. D. Wikström. *How to Implement a Stand-alone Verifier for the Verificatum Mix-Net VMN Version 3.1.0*, 2022-09-10, https://www.verificatum.org/files/vmnv-3.1.0.pdf
7. John Caron, Dan Wallach, *ElectionGuard Kotlin library*, https://github.com/votingworks/electionguard-kotlin-multiplatform
8. E-Voting Group, Institute for Cybersecurity and Engineering, Bern University of Applied Sciences, *OpenCHVote*, https://gitlab.com/openchvote/cryptographic-protocol
9. Thomas Haines, *A Description and Proof of a Generalised and Optimised Variant of Wikström’s Mixnet*, arXiv:1901.08371v1 [cs.CR], 24 Jan 2019
   

