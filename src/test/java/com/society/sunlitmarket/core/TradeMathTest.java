package com.society.sunlitmarket.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TradeMathTest {
    @Test void disabledDirectionsStayDisabled(){assertEquals(0,TradeMath.price(0,1.5));assertEquals(0,TradeMath.price(-1,1));}
    @Test void validPositivePricesRoundOnce(){assertEquals(72,TradeMath.price(90,.8));assertEquals(84,TradeMath.price(70,1.2));assertEquals(1,TradeMath.price(1,.1));}
    @Test void invalidMultipliersAreRejected(){for(double d:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->TradeMath.price(5,d));}
    @Test void overflowingAmountsAreRejected(){assertThrows(IllegalArgumentException.class,()->TradeMath.amount((long)Integer.MAX_VALUE+1));assertThrows(IllegalArgumentException.class,()->TradeMath.amount(-1));}
    @Test void clampUsesLongBeforeLimiting(){assertEquals(3000,TradeMath.clamp((long)Integer.MAX_VALUE+1,-1000,3000));}
    @Test void firstDayAndBackwardsDaysDoNotDecay(){assertEquals(0,TradeMath.days(Long.MIN_VALUE,100));assertEquals(0,TradeMath.days(10,9));assertEquals(0,TradeMath.days(10,10));}
    @Test void jumpedDaysCountElapsedDays(){assertEquals(3,TradeMath.days(10,13));assertEquals(197,TradeMath.decay(200,3,1));assertEquals(-47,TradeMath.decay(-50,3,1));}
    @Test void decayNeverCrossesZero(){assertEquals(0,TradeMath.decay(2,10,1));assertEquals(0,TradeMath.decay(-2,10,1));}
    @Test void zeroDecayKeepsValue(){assertEquals(200,TradeMath.decay(200,500,0));}
}
