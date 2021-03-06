import cats.free.{Free, FreeApplicative, Inject}
import cats.{~>, Applicative, Monad}

package object freestyle {

  /**
   * A sequential series of parallel program fragments.
   *
   * originally named `SeqPar` and some of the relating functions below originated from a translation into Scala
   * from John De Goes' original gist which can be found at
   * https://gist.github.com/jdegoes/dfaa07042f51245fa09716c6387aa5a6
   */
  type FreeS[F[_], A] = Free[FreeApplicative[F, ?], A]

  /** Interprets a parallel fragment `f` into `g` */
  type ParInterpreter[F[_], G[_]] = FreeApplicative[F, ?] ~> G

  /**
   * Optimizes a parallel fragment `f` into a sequential series of parallel
   * program fragments in `g`.
   */
  type ParOptimizer[F[_], G[_]] = ParInterpreter[F, FreeS[G, ?]]

  object FreeS {

    type Par[F[_], A] = FreeApplicative[F, A]

    /** Lift an `F[A]` value into `FreeS[F, A]` */
    def liftFA[F[_], A](fa: F[A]): FreeS[F, A] =
      Free.liftF(FreeApplicative.lift(fa))

    /** Lift a sequential `Free[F, A]` into `FreeS[F, A]` */
    def liftSeq[F[_], A](free: Free[F, A]): FreeS[F, A] =
      free.compile(λ[(F ~> FreeS.Par[F, ?])](fa => FreeApplicative.lift(fa)))

    /** Lift a parallel `FreeApplicative[F, A]` into `FreeS[F, A]` */
    def liftPar[F[_], A](freeap: FreeS.Par[F, A]): FreeS[F, A] =
      Free.liftF(freeap)

    def inject[F[_], G[_]]: FreeSParInjectPartiallyApplied[F, G] =
      new FreeSParInjectPartiallyApplied

    /**
     * Pre-application of an injection to a `F[A]` value.
     */
    final class FreeSParInjectPartiallyApplied[F[_], G[_]] {
      def apply[A](fa: F[A])(implicit I: Inject[F, G]): FreeS.Par[G, A] =
        FreeApplicative.lift(I.inj(fa))
    }

  }

  /**
   * Syntax functions for FreeS.Par
   */
  implicit class FreeSOps[F[_], A](private val fa: FreeS[F, A]) extends AnyVal {

    /**
     * Applies the most general optimization from a parallel program fragment
     * in `f` to a sequential.
     */
    def optimize[G[_]](opt: ParOptimizer[F, G]): FreeS[G, A] =
      fa.foldMap(opt)

    /** Applies a parallel-to-parallel optimization */
    def parOptimize[G[_]](opt: ParInterpreter[F, FreeS.Par[G, ?]]): FreeS[G, A] =
      fa.compile(opt)

    /**
     * Runs a seq/par program by converting each parallel fragment in `f` into an `H`
     * `H` should probably be an `IO`/`Task` like `Monad` also providing parallel execution.
     */
    def exec[H[_]: Monad](implicit interpreter: ParInterpreter[F, H]): H[A] =
      fa.foldMap(interpreter)
  }

  /**
   * Syntax functions for FreeS.Par
   */
  implicit class FreeSParSyntax[F[_], A](private val fa: FreeS.Par[F, A]) extends AnyVal {

    /**
     * Back to sequential computation in the context of FreeS
     */
    def freeS: FreeS[F, A] = FreeS.liftPar(fa)

    def exec[G[_]: Applicative](implicit interpreter: F ~> G): G[A] =
      fa.foldMap(interpreter)
  }

  /**
   * Syntax functions for any F[A]
   */
  implicit class ParLift[F[_], A](private val fa: F[A]) extends AnyVal {

    /**
     * Lift an F[A] into a FreeS[F, A]
     */
    def freeS: FreeS[F, A] = FreeS.liftFA(fa)
  }

  implicit class FreeSLiftSyntax[G[_], A](ga: G[A]) {
    def liftFS[F[_]](implicit L: FreeSLift[F, G]): FreeS[F, A]        = L.liftFS(ga)
    def liftFSPar[F[_]](implicit L: FreeSLift[F, G]): FreeS.Par[F, A] = L.liftFSPar(ga)
  }

  implicit def freeSPar2FreeSMonad[F[_], A](fa: FreeS.Par[F, A]): FreeS[F, A] = fa.freeS

}
