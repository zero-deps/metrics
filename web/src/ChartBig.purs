module BigChart
  ( reactClass
  ) where

import Data.Maybe (Maybe(Just, Nothing))
import Effect (Effect)
import Effect.Console (error)
import Effect.Ref as E
import Prelude hiding (div)
import React (ReactClass, component, getProps)
import React.DOM (canvas)
import ReactOps (Ref, ref', createRef)
import Schema (NumPoint)

type State = {}
type Props =
  { cpuPoints :: Array NumPoint
  , memPoints :: Array NumPoint
  , actPoints :: Array NumPoint
  , actLabels :: Array String
  }

reactClass :: ReactClass Props
reactClass = component "BigChart" \this -> do
  props <- getProps this
  let r = createRef
  chart <- E.new Nothing
  pure
    { state: {}
    , render: pure $ canvas [ ref' r ] []
    , componentDidMount: do
        c <- E.read chart
        case c of
          Just _ -> error "chart already exists"
          Nothing -> do
            c' <- createChart r props
            E.write (Just c') chart
    , componentDidUpdate: \p _ _ -> do
        c <- E.read chart
        case c of
          Just c' -> updateChart c' p
          Nothing -> error "chart doesn't exists"
    , componentWillUnmount: do
        c <- E.read chart
        case c of
          Just c' -> do
            destroyChart c'
            E.write Nothing chart
          Nothing -> error "chart doesn't exists"
    }

foreign import data Chart :: Type

foreign import createChart
  :: Ref
  -> Props
  -> Effect Chart
foreign import updateChart
  :: Chart
  -> Props
  -> Effect Unit
foreign import destroyChart
  :: Chart
  -> Effect Unit
